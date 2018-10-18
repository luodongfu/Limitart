/*
 * Copyright (c) 2016-present The Limitart Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package top.limitart.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.limitart.base.Conditions;
import top.limitart.base.Proc1;
import top.limitart.util.ReflectionUtil;
import top.limitart.util.StringUtil;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * RPC调用端
 *
 * @author hank
 * @version 2018/10/18 0018 17:00
 */
public abstract class RPCClient {
    private static Logger LOGGER = LoggerFactory.getLogger(RPCClient.class);
    // 动态代理集合
    private Map<Class<?>, Object> clientProxies = new HashMap<>();
    // RequestId生成器
    private AtomicInteger requestIDCreator = new AtomicInteger(0);
    // RPC调用回调集合
    private Map<Integer, RPCRemoteCallFuture> futures = new ConcurrentHashMap<>();
    private LongAdder dropNum = new LongAdder();

    /**
     * 指定一个代理
     *
     * @param interfaceClss
     * @param <T>
     * @return
     */
    public <T> T create(Class<T> interfaceClss) {
        Object proxyObject = this.clientProxies.get(interfaceClss);
        return (T) proxyObject;
    }

    public void loadPackage(String[] packages) throws IOException, ReflectiveOperationException, PRCServiceProxyException {
        List<Class<?>> classesByPackage = new ArrayList<>();
        for (String temp : packages) {
            classesByPackage.addAll(ReflectionUtil.getClassesBySuperClass(temp, Object.class));
        }
        for (Class<?> clzz : classesByPackage) {
            loadProxy(clzz);
        }
    }

    public void loadProxy(Class<?> clzz) throws PRCServiceProxyException {
        RPCService annotation = clzz.getAnnotation(RPCService.class);
        if (annotation == null) {
            return;
        }
        if (!clzz.isInterface()) {
            throw new PRCServiceProxyException("RPC service must be an interface,error clazz:" + clzz.getName());
        }
        String provider = annotation.value();
        if (StringUtil.empty(provider)) {
            throw new PRCServiceProxyException("service：" + clzz.getName() + " provider null！");
        }
        // 检查方法
        Method[] methods = clzz.getMethods();
        for (Method method : methods) {
            String methodOverloadName = ReflectionUtil.getMethodOverloadName(method);
            // 检查参数
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                Conditions.args(checkParamType(parameterTypes[i]), "{} param type error on index:{}", methodOverloadName, i);
            }
            Conditions.args(checkParamType(method.getReturnType()), "{} param type error on return", methodOverloadName);
            // 异常抛出检查
            Class<?>[] exceptionTypes = method.getExceptionTypes();
            if (exceptionTypes == null || exceptionTypes.length < 1) {
                throw new PRCServiceProxyException("class " + clzz.getName() + " method " + methodOverloadName + " must contains Exception");
            }
            boolean exOk = false;
            for (Class<?> ex : exceptionTypes) {
                if (ex == Exception.class) {
                    exOk = true;
                }
            }
            if (!exOk) {
                throw new PRCServiceProxyException("class " + clzz.getName() + " method " + methodOverloadName + " must contains Exception"
                );
            }
        }
        // 创建动态代理类
        Object newProxyInstance = ReflectionUtil.newProxy(clzz,
                (proxy, method, args) -> {
                    if (proxy != null) {
                        if ("equals".equals(method.getName())) {
                            return proxy == args[0];
                        } else if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        } else if ("toString".equals(method.getName())) {
                            return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
                        }
                    }
                    RPCServiceName serviceName = new RPCServiceName(provider, ReflectionUtil.getMethodOverloadName(method), annotation.version());
                    return proxyExecute(serviceName, args, null);
                });
        clientProxies.put(clzz, newProxyInstance);
        LOGGER.info("create rpc proxy：{},provider：{},instance：{}", clzz.getName(), provider, newProxyInstance);
    }

    private Object proxyExecute(RPCServiceName serviceName, Object[] args, Proc1<Object> callback)
            throws InterruptedException {
        int max = 100;
        if (futures.size() > max) {
            dropNum.increment();
            throw new RPCServiceExecuteException("call back queue size oversize：" + max + ",drop request,drops:" + dropNum.longValue()
            );
        }
        RPCRemoteCallFuture future = rpcSend(serviceName, args, callback);
        if (!future.completed()) {
            // 无条件线程等待
            boolean await = future.await(3000,
                    TimeUnit.MILLISECONDS);
            futures.remove(future.getRequestID());
            if (!await) {
                throw new RPCServiceExecuteException(
                        "method：" + serviceName + ",overtime,ID：" + future.getRequestID());
            }
        }
        // 等待完成设置回调完成，服务器处理完毕后就不用唤醒此线程
        if (future.getErrorCode() == RPCServiceErrorCode.SUCCESS) {
            return future.getReturnVal();
        }
        throw new RPCServiceExecuteException(serviceName + " error code:" + future.getErrorCode());
    }

    /**
     * 发送到RPC请求到服务器
     *
     * @param serviceName
     * @param args
     * @return
     * @throws Exception
     */
    private RPCRemoteCallFuture rpcSend(RPCServiceName serviceName, Object[] args
            , Proc1<Object> callback) {
        // 开始构造消息
        int requestId = requestIDCreator.incrementAndGet();
        RPCRemoteCallFuture future = new RPCRemoteCallFuture(requestId, callback);
        futures.put(future.getRequestID(), future);
        if (futures.size() > 100) {
            LOGGER.error("警告！开始动态代理方法：" + serviceName + "，回调列表长度：" + futures.size()
                    + "(并发量)，id：" + requestId);
        }
        //TODO
        return future;
    }

    private void onRPCResonse(int requestID, int errorCode, Object returnVal) {
        RPCRemoteCallFuture future = futures.get(requestID);
        if (future == null) {
            LOGGER.error("requestId:{} can not find!", requestID);
            return;
        }
        future.complete();
        future.setReturnVal(returnVal);
        future.setErrorCode(errorCode);
        // 不是异步回调
        if (future.getCallback() == null) {
            // 唤醒线程
            future.countDown();
        } else {
            futures.remove(requestID);
            if (errorCode == RPCServiceErrorCode.SUCCESS) {
                future.getCallback().run(returnVal);
            }
        }
    }

    protected abstract boolean checkParamType(Class<?> paramType);
}
