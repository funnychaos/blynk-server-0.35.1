package cc.blynk.server.application.handlers.main.logic;

import cc.blynk.server.Holder;
import cc.blynk.server.application.handlers.main.auth.AppStateHolder;
import cc.blynk.server.core.dao.SessionDao;
import cc.blynk.server.core.dao.TokenManager;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.App;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.protocol.exceptions.NotAllowedException;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.workers.timer.TimerWorker;
import cc.blynk.utils.ArrayUtil;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;
import java.util.List;

import static cc.blynk.server.internal.CommonByteBufUtil.ok;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 01.02.16.
 */
public final class DeleteAppLogic {

    private final TokenManager tokenManager;
    private final TimerWorker timerWorker;
    private final SessionDao sessionDao;

    public DeleteAppLogic(Holder holder) {
        this.tokenManager = holder.tokenManager;
        this.timerWorker = holder.timerWorker;
        this.sessionDao = holder.sessionDao;
    }

    public void messageReceived(ChannelHandlerContext ctx, AppStateHolder state, StringMessage message) {
        String id = message.body;

        User user = state.user;

        int existingAppIndex = user.profile.getAppIndexById(id);

        if (existingAppIndex == -1) {
            throw new NotAllowedException("App with passed is not exists.", message.id);
        }

        int[] projectIds = user.profile.apps[existingAppIndex].projectIds;

        List<DashBoard> result = new ArrayList<>();
        for (DashBoard dash : user.profile.dashBoards) {
            if (ArrayUtil.contains(projectIds, dash.id)) {
                timerWorker.deleteTimers(state.userKey, dash);
                tokenManager.deleteDash(dash);
                sessionDao.closeHardwareChannelByDashId(state.userKey, dash.id);
            } else {
                result.add(dash);
            }
        }

        user.profile.dashBoards = result.toArray(new DashBoard[result.size()]);
        user.profile.apps = ArrayUtil.remove(user.profile.apps, existingAppIndex, App.class);
        user.lastModifiedTs = System.currentTimeMillis();

        ctx.writeAndFlush(ok(message.id), ctx.voidPromise());
    }

}
