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

import java.util.Objects;

/**
 * RPC服务全名称
 *
 * @author hank
 * @version 2018/10/18 0018 20:47
 */
public class RPCServiceName {
    //提供商名称(可理解未命名空间) +模块名称(可理解为类名)
    private RPCModuleName moduleName;
    //方法名称(可理解为类里的方法)
    private String methodName;
    //模块的版本(客户端和服务器版本不同不能调用)
    private int version;

    public RPCServiceName(RPCModuleName moduleName, String methodName, int version) {
        this.moduleName = moduleName;
        this.methodName = methodName;
        this.version = version;
    }

    @Override
    public String toString() {
        return this.moduleName + "/" + this.methodName + "/" + this.version;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        RPCServiceName that = (RPCServiceName) object;
        return version == that.version &&
                Objects.equals(moduleName, that.moduleName) &&
                Objects.equals(methodName, that.methodName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleName, methodName, version);
    }

    public RPCModuleName getModuleName() {
        return moduleName;
    }

    public String getMethodName() {
        return methodName;
    }

    public int getVersion() {
        return version;
    }
}
