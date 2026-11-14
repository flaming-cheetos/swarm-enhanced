package com.rdh.swarm.web;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

import java.util.Map;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class SwarmWebHandler extends SimpleChannelInboundHandler<Object> {

    private final SwarmWebServer server;
    private WebSocketServerHandshaker handshaker;

    public SwarmWebHandler(SwarmWebServer server) {
        this.server = server;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest req) {
            handleHttp(ctx, req);
        } else if (msg instanceof WebSocketFrame frame) {
            handleWebSocket(ctx, frame);
        }
    }

    private void handleHttp(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!server.isAuthorized(req)) {
            sendResponse(ctx, UNAUTHORIZED, "text/plain", "Unauthorized");
            return;
        }
        if (!server.isOriginAllowed(req)) {
            sendResponse(ctx, FORBIDDEN, "text/plain", "Origin not allowed");
            return;
        }

        String uri = req.uri().split("\\?")[0];

        if (uri.equals("/api/action/check_fuel")) {
            sendResponse(ctx, NOT_IMPLEMENTED, "application/json",
                "{\"ok\":false,\"error\":\"check_fuel unavailable: container reader not implemented\"}");
            return;
        }

        // WebSocket upgrade
        if (uri.equals("/ws")) {
            WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
                "ws://" + req.headers().get(HttpHeaderNames.HOST) + "/ws",
                null, true
            );
            handshaker = factory.newHandshaker(req);
            if (handshaker == null) {
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            } else {
                handshaker.handshake(ctx.channel(), req).addListener(future -> {
                    if (future.isSuccess()) {
                        server.addClient(ctx.channel());
                    }
                });
            }
            return;
        }

        // API POST endpoints
        if (req.method() == HttpMethod.POST) {
            String body = req.content().toString(CharsetUtil.UTF_8);
            String debug = server.handleDebugApi(uri, body);
            if (debug != null) {
                sendJson(ctx, debug);
                return;
            }
            server.handleApiCommand(uri, body);
            sendJson(ctx, "{\"ok\":true}");
            return;
        }

        // Serve HTML for GET /
        if (req.method() == HttpMethod.GET && uri.equals("/")) {
            sendHtml(ctx, SwarmWebPanel.getHtml(), req.headers().get(HttpHeaderNames.HOST));
            return;
        }

        if (req.method() == HttpMethod.GET && uri.equals("/app.js")) {
            sendJs(ctx, SwarmWebPanelScript.getScript());
            return;
        }

        // Web v2: additive page and classpath assets. / is untouched.
        if (req.method() == HttpMethod.GET && (uri.equals("/v2") || uri.equals("/v2/"))) {
            if (!SwarmWebPanelV2.isAvailable()) {
                sendResponse(ctx, INTERNAL_SERVER_ERROR, "text/plain", "v2 assets missing");
                return;
            }
            sendHtml(ctx, SwarmWebPanelV2.html(server.token()), req.headers().get(HttpHeaderNames.HOST));
            return;
        }

        if (req.method() == HttpMethod.GET && uri.equals("/v2/app.css")) {
            sendResource(ctx, "text/css; charset=UTF-8", SwarmWebPanelV2.css());
            return;
        }

        if (req.method() == HttpMethod.GET && uri.equals("/v2/app.js")) {
            sendResource(ctx, "application/javascript; charset=UTF-8", SwarmWebPanelV2.js());
            return;
        }

        if (req.method() == HttpMethod.GET && uri.equals("/api/items")) {
            sendJson(ctx, server.buildItemsJson());
            return;
        }

        // 404
        sendResponse(ctx, NOT_FOUND, "text/plain", "Not found");
    }

    private void handleWebSocket(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            server.removeClient(ctx.channel());
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        } else if (frame instanceof TextWebSocketFrame text) {
            // Handle messages from browser (currently unused, all control via HTTP POST)
            String msg = text.text();
            server.handleApiCommand(msg, msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        server.removeClient(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

    private void sendHtml(ChannelHandlerContext ctx, String html, String host) {
        String wsSource = host == null || host.isBlank() ? "" : " ws://" + host;
        String csp = "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; connect-src 'self'" + wsSource
            + "; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
        sendResponse(ctx, OK, "text/html; charset=UTF-8", html, Map.of(
            HttpHeaderNames.SET_COOKIE.toString(), server.sessionCookie(),
            "Content-Security-Policy", csp
        ));
    }

    private void sendJs(ChannelHandlerContext ctx, String js) {
        sendResponse(ctx, OK, "application/javascript; charset=UTF-8", js);
    }

    private void sendResource(ChannelHandlerContext ctx, String contentType, String body) {
        if (body == null || body.isEmpty()) {
            sendResponse(ctx, NOT_FOUND, "text/plain", "Not found");
            return;
        }
        sendResponse(ctx, OK, contentType, body);
    }

    private void sendJson(ChannelHandlerContext ctx, String json) {
        sendResponse(ctx, OK, "application/json", json);
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status,
                               String contentType, String body) {
        sendResponse(ctx, status, contentType, body, Map.of());
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status,
                               String contentType, String body, Map<String, String> extraHeaders) {
        byte[] bytes = body.getBytes(CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
            HTTP_1_1, status, Unpooled.wrappedBuffer(bytes)
        );
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, contentType)
            .set(HttpHeaderNames.CONTENT_LENGTH, bytes.length)
            .set("X-Content-Type-Options", "nosniff")
            .set("Referrer-Policy", "no-referrer")
            .set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        extraHeaders.forEach((name, value) -> response.headers().set(name, value));
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
