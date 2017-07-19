package org.slingerxv.limitart.net.http;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.slingerxv.limitart.collections.ConstraintMap;
import org.slingerxv.limitart.funcs.Proc1;
import org.slingerxv.limitart.funcs.Proc2;
import org.slingerxv.limitart.net.define.AbstractNettyServer;
import org.slingerxv.limitart.net.define.IServer;
import org.slingerxv.limitart.net.http.codec.QueryStringDecoderV2;
import org.slingerxv.limitart.net.http.constant.QueryMethod;
import org.slingerxv.limitart.net.http.constant.RequestErrorCode;
import org.slingerxv.limitart.net.http.handler.HttpHandler;
import org.slingerxv.limitart.net.http.message.UrlMessage;
import org.slingerxv.limitart.net.http.message.UrlMessageFactory;
import org.slingerxv.limitart.net.http.util.HttpUtil;
import org.slingerxv.limitart.util.StringUtil;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * Http服务器
 * 
 * @author Hank
 *
 */
@Sharable
public class HttpServer extends AbstractNettyServer implements IServer {
	private static Logger log = LogManager.getLogger();
	private ServerBootstrap boot;
	private Channel channel;
	// config
	private String serverName;
	private int port;
	// 消息聚合最大（1024KB）,即Content-Length
	private int httpObjectAggregatorMax;
	private UrlMessageFactory facotry;
	private HashSet<String> whiteList;
	// listener
	private Proc1<Channel> onServerBind;
	private Proc2<Channel, Boolean> onChannelStateChanged;
	private Proc2<UrlMessage, ConstraintMap<String>> dispatchMessage;
	private Proc2<Channel, HttpMessage> onMessageOverSize;
	private Proc2<Channel, Throwable> onExceptionCaught;

	private HttpServer(HttpServerBuilder builder) {
		this.port = builder.port;
		this.httpObjectAggregatorMax = builder.httpObjectAggregatorMax;
		this.serverName = builder.serverName;
		this.whiteList = builder.whiteList;
		if (builder.facotry == null) {
			throw new NullPointerException("factory");
		}
		this.facotry = builder.facotry;
		this.onServerBind = builder.onServerBind;
		this.onChannelStateChanged = builder.onChannelStateChanged;
		this.dispatchMessage = builder.dispatchMessage;
		this.onMessageOverSize = builder.onMessageOverSize;
		this.onExceptionCaught = builder.onExceptionCaught;
		boot = new ServerBootstrap();
		if (Epoll.isAvailable()) {
			boot.channel(EpollServerSocketChannel.class).option(ChannelOption.SO_BACKLOG, 1024);
			log.info(serverName + " epoll init");
		} else {
			boot.channel(NioServerSocketChannel.class);
			log.info(serverName + " nio init");
		}
		boot.group(bossGroup, workerGroup).option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
				.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
				.childHandler(new ChannelInitializer<SocketChannel>() {

					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ch.pipeline().addLast(new HttpRequestDecoder())
								.addLast(new HttpObjectAggregator(httpObjectAggregatorMax) {
									@Override
									protected void handleOversizedMessage(ChannelHandlerContext ctx,
											HttpMessage oversized) throws Exception {
										Exception e = new Exception(
												ctx.channel() + " : " + oversized + " is over size");
										log.error(e, e);
										if (onMessageOverSize != null) {
											onMessageOverSize.run(ctx.channel(), oversized);
										}
									}
								}).addLast(new HttpContentCompressor()).addLast(new HttpContentDecompressor())
								.addLast(new HttpResponseEncoder()).addLast(new ChunkedWriteHandler())
								.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {

									@Override
									protected void channelRead0(ChannelHandlerContext arg0, FullHttpRequest arg1)
											throws Exception {
										channelRead00(arg0, arg1);
									}

									@Override
									public void channelActive(ChannelHandlerContext ctx) throws Exception {
										if (onChannelStateChanged != null) {
											onChannelStateChanged.run(ctx.channel(), true);
										}
									}

									@Override
									public void channelInactive(ChannelHandlerContext ctx) throws Exception {
										if (whiteList != null && !whiteList.isEmpty()) {
											InetSocketAddress insocket = (InetSocketAddress) ctx.channel()
													.remoteAddress();
											String remoteAddress = insocket.getAddress().getHostAddress();
											if (!whiteList.contains(remoteAddress)) {
												ctx.channel().close();
												log.error("ip: " + remoteAddress + " rejected link!");
												return;
											}
										}
										if (onChannelStateChanged != null) {
											onChannelStateChanged.run(ctx.channel(), false);
										}
									}

									@Override
									public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
											throws Exception {
										log.error(ctx.channel() + " cause:", cause);
										if (onExceptionCaught != null) {
											onExceptionCaught.run(ctx.channel(), cause);
										}
									}
								});
					}
				});
	}

	@Override
	public void startServer() {
		new Thread(() -> {
			try {
				boot.bind(port).addListener((ChannelFutureListener) channelFuture -> {
					if (channelFuture.isSuccess()) {
						channel = channelFuture.channel();
						log.info(serverName + " bind at port:" + port);
						if (onServerBind != null) {
							onServerBind.run(channel);
						}
					}
				}).sync().channel().closeFuture().sync();
			} catch (InterruptedException e) {
				log.error(e, e);
			}
		}, serverName + "-Binder").start();
	}

	@Override
	public void stopServer() {
		if (channel != null) {
			channel.close();
			channel = null;
		}
	}

	public String getServerName() {
		return this.serverName;
	}

	public int getPort() {
		return port;
	}

	public int getHttpObjectAggregatorMax() {
		return httpObjectAggregatorMax;
	}

	public HashSet<String> getWhiteList() {
		return whiteList;
	}

	public UrlMessageFactory getFacotry() {
		return facotry;
	}

	public Proc1<Channel> getOnServerBind() {
		return onServerBind;
	}

	private void channelRead00(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
		if (!msg.decoderResult().isSuccess()) {
			HttpUtil.sendResponseError(ctx.channel(), RequestErrorCode.ERROR_DECODE_FAIL);
			return;
		}
		if (StringUtil.isEmptyOrNull(msg.uri())) {
			HttpUtil.sendResponseError(ctx.channel(), RequestErrorCode.ERROR_URL_EMPTY);
			return;
		}
		String url;
		ConstraintMap<String> params = new ConstraintMap<>();
		if (msg.method() == GET) {
			QueryStringDecoderV2 queryStringDecoder = new QueryStringDecoderV2(msg.uri());
			url = queryStringDecoder.path();
			params = queryStringDecoder.parameters();
		} else if (msg.method() == POST) {
			url = msg.uri();
		} else {
			HttpUtil.sendResponseError(ctx.channel(), RequestErrorCode.ERROR_METHOD_FORBBIDEN);
			return;
		}
		if (url.equals("/2016info")) {
			HttpUtil.sendResponse(ctx.channel(), HttpResponseStatus.OK, "hello~stupid!", true);
			return;
		}
		UrlMessage message = facotry.getMessage(url);
		if (message == null) {
			log.error("消息不存在:" + url);
			HttpUtil.sendResponseError(ctx.channel(), RequestErrorCode.ERROR_URL_FORBBIDEN);
			return;
		}
		if (message.getMethod() == null) {
			HttpUtil.sendResponseError(ctx.channel(), RequestErrorCode.ERROR_METHOD_FORBBIDEN);
			return;
		}
		// 如果为POST，那么只能POST,如果是Get，那么都可以
		if (message.getMethod() == QueryMethod.POST && msg.method() != POST) {
			HttpUtil.sendResponseError(ctx.channel(), RequestErrorCode.ERROR_METHOD_ERROR);
			return;
		}
		@SuppressWarnings("unchecked")
		HttpHandler<UrlMessage> handler = (HttpHandler<UrlMessage>) facotry.getHandler(url);
		if (handler == null) {
			HttpUtil.sendResponseError(ctx.channel(), RequestErrorCode.ERROR_URL_FORBBIDEN);
			return;
		}
		message.setChannel(ctx.channel());
		message.setHandler(handler);
		// 如果是POST，最后再来解析参数
		if (msg.method() == POST) {
			try {
				HttpPostRequestDecoder postDecoder = new HttpPostRequestDecoder(msg);
				List<InterfaceHttpData> postData = postDecoder.getBodyHttpDatas();
				for (InterfaceHttpData data : postData) {
					if (data instanceof Attribute) {
						Attribute at = (Attribute) data;
						String name = at.getName();
						String value = at.getValue();
						params.putObject(name, value);
					} else if (data instanceof FileUpload) {
						FileUpload fileUpload = (FileUpload) data;
						int readableBytes = fileUpload.content().readableBytes();
						// 没内容的文件GG掉
						if (readableBytes > 0) {
							String name = fileUpload.getFilename();
							byte[] file = fileUpload.content().array();
							message.getFiles().put(name, file);
						}
					}
				}
			} catch (Exception e) {
				log.error(e, e);
				HttpUtil.sendResponseError(ctx.channel(), RequestErrorCode.ERROR_POST_ERROR);
				return;
			}
		}
		try {
			Field[] declaredFields = message.getClass().getDeclaredFields();
			for (Field field : declaredFields) {
				if (Modifier.isTransient(field.getModifiers())) {
					continue;
				}
				field.setAccessible(true);
				Object object = params.getObject(field.getName());
				if (object != null) {
					field.set(message, object);
				}
			}
		} catch (Exception e) {
			HttpUtil.sendResponseError(ctx.channel(), RequestErrorCode.ERROR_MESSAGE_PARSE, e.getMessage());
			return;
		}
		if (dispatchMessage != null) {
			dispatchMessage.run(message, params);
		} else {
			log.warn(serverName + " no dispatch message listener!");
		}
	}

	public static class HttpServerBuilder {
		private String serverName;
		private int port;
		private int httpObjectAggregatorMax;
		private UrlMessageFactory facotry;
		private HashSet<String> whiteList;
		// listener
		private Proc1<Channel> onServerBind;
		private Proc2<Channel, Boolean> onChannelStateChanged;
		private Proc2<UrlMessage, ConstraintMap<String>> dispatchMessage;
		private Proc2<Channel, HttpMessage> onMessageOverSize;
		private Proc2<Channel, Throwable> onExceptionCaught;

		public HttpServerBuilder() {
			this.serverName = "Http-Server";
			this.port = 8080;
			this.httpObjectAggregatorMax = 1024 * 1024;
			this.whiteList = new HashSet<>();
		}

		public HttpServer build() {
			return new HttpServer(this);
		}

		public HttpServerBuilder serverName(String serverName) {
			this.serverName = serverName;
			return this;
		}

		public HttpServerBuilder port(int port) {
			if (port >= 1024) {
				this.port = port;
			}
			return this;
		}

		public HttpServerBuilder httpObjectAggregatorMax(int httpObjectAggregatorMax) {
			this.httpObjectAggregatorMax = Math.max(512, httpObjectAggregatorMax);
			return this;
		}

		public HttpServerBuilder whiteList(String... remoteAddress) {
			for (String ip : remoteAddress) {
				if (StringUtil.isIp(ip)) {
					this.whiteList.add(ip);
				}
			}
			return this;
		}

		public HttpServerBuilder factory(UrlMessageFactory factory) {
			this.facotry = factory;
			return this;
		}

		public HttpServerBuilder onServerBind(Proc1<Channel> onServerBind) {
			this.onServerBind = onServerBind;
			return this;
		}

		public HttpServerBuilder onChannelStateChanged(Proc2<Channel, Boolean> onChannelStateChanged) {
			this.onChannelStateChanged = onChannelStateChanged;
			return this;
		}

		public HttpServerBuilder dispatchMessage(Proc2<UrlMessage, ConstraintMap<String>> dispatchMessage) {
			this.dispatchMessage = dispatchMessage;
			return this;
		}

		public HttpServerBuilder onMessageOverSize(Proc2<Channel, HttpMessage> onMessageOverSize) {
			this.onMessageOverSize = onMessageOverSize;
			return this;
		}

		public HttpServerBuilder onExceptionCaught(Proc2<Channel, Throwable> onExceptionCaught) {
			this.onExceptionCaught = onExceptionCaught;
			return this;
		}
	}
}