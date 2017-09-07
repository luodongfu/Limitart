/*
 * Copyright (c) 2016-present The Limitart Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.slingerxv.limitart.net.define;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * @author hank
 *
 */
public abstract class AbstractNettyClient {
	private static Logger log = LoggerFactory.getLogger(AbstractNettyServer.class);
	protected static EventLoopGroup workerGroup = new NioEventLoopGroup();
	private Bootstrap bootstrap;
	private String clientName;
	private Channel channel;
	private int autoReconnect;

	protected AbstractNettyClient(String clientName, int autoReconnect) {
		this.clientName = Objects.requireNonNull(clientName, "client name");
		this.autoReconnect = autoReconnect;
		bootstrap = new Bootstrap();
		log.info(clientName + " nio init");
		bootstrap.group(workerGroup).channel(NioSocketChannel.class)
				.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
				.handler(new ChannelInitializer<SocketChannel>() {

					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						initPipeline(ch.pipeline());
						ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
							@Override
							public boolean isSharable() {
								return true;
							}

							@Override
							public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
								channelRead0(ctx, msg);
							}

							@Override
							public void channelInactive(ChannelHandlerContext ctx) throws Exception {
								log.info(getClientName() + " disconnected!");
								channelInactive0(ctx);
							}

							@Override
							public void channelActive(ChannelHandlerContext ctx) throws Exception {
								channel = ctx.channel();
								log.info(getClientName() + " connected!");
								channelActive0(ctx);
							}

							@Override
							public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
								log.error(ctx.channel() + " cause:", cause);
								exceptionCaught0(ctx, cause);
							}
						});
					}
				});
	}

	protected abstract void initPipeline(ChannelPipeline pipeline);

	protected abstract void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception;

	protected abstract void channelInactive0(ChannelHandlerContext ctx) throws Exception;

	protected abstract void channelActive0(ChannelHandlerContext ctx) throws Exception;

	protected abstract void exceptionCaught0(ChannelHandlerContext ctx, Throwable cause) throws Exception;

	protected AbstractNettyClient tryDisConnect() {
		if (channel != null) {
			channel.close();
			channel = null;
		}
		return this;
	}

	public abstract void connect();

	public abstract void disConnect();

	private void connect0(String ip, int port) {
		if (channel != null && channel.isWritable()) {
			return;
		}
		log.info(clientName + " start connect server：" + ip + ":" + port + "...");
		try {
			bootstrap.connect(ip, port).sync().addListener((ChannelFutureListener) channelFuture -> {
				log.info(clientName + " connect server：" + ip + ":" + port + " success！");
			});
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			if (autoReconnect > 0) {
				tryReconnect(ip, port, autoReconnect);
			}
		}
	}

	protected void tryReconnect(String ip, int port, int waitSeconds) {
		if (channel != null) {
			channel.close();
			channel = null;
		}
		log.info(clientName + " try connect server：" + ip + ":" + port + " after " + waitSeconds + " seconds");
		if (waitSeconds > 0) {
			workerGroup.schedule(() -> {
				connect0(ip, port);
			}, waitSeconds, TimeUnit.SECONDS);
		} else {
			connect0(ip, port);
		}
	}

	public String getClientName() {
		return clientName;
	}

	public int getAutoReconnect() {
		return autoReconnect;
	}

	public Channel channel() {
		return channel;
	}
}
