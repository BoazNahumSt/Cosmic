package net.netty;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import service.TransitionService;

@Sharable
public class DisconnectHandler extends ChannelInboundHandlerAdapter {
    private final TransitionService transitionService;

    public DisconnectHandler(TransitionService transitionService) {
        this.transitionService = transitionService;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof DisconnectException de) {
            var client = de.getClient();
            transitionService.disconnect(client, true);
        } else {
            ctx.fireExceptionCaught(cause);
        }
    }

}
