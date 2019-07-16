package com.ucloud.library.netanalysis.command.net.traceroute;


import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.ucloud.library.netanalysis.command.bean.UCommandStatus;
import com.ucloud.library.netanalysis.command.net.UNetCommandTask;
import com.ucloud.library.netanalysis.utils.JLog;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Created by joshua on 2018/9/4 10:21.
 * Company: UCloud
 * E-mail: joshua.yin@ucloud.cn
 */
final class TracerouteTask extends UNetCommandTask<TracerouteNodeResult> {
    private InetAddress targetAddress;
    private int count;
    private int hop;
    private int currentCount;
    
    private TracerouteCallback2 callback;
    
    TracerouteTask(@NonNull InetAddress targetAddress, int hop) {
        this(targetAddress, hop, 3, null);
    }
    
    TracerouteTask(@NonNull InetAddress targetAddress, int hop, TracerouteCallback2 callback) {
        this(targetAddress, hop, 3, callback);
    }
    
    TracerouteTask(@NonNull InetAddress targetAddress, int hop, int count, TracerouteCallback2 callback) {
        this.targetAddress = targetAddress;
        this.hop = hop;
        this.count = count;
        this.callback = callback;
    }
    
    @Override
    public TracerouteNodeResult call() throws Exception {
        return running();
    }
    
    protected TracerouteNodeResult running() {
        isRunning = true;
        
        String targetIp = targetAddress == null ? "" : targetAddress.getHostAddress();
        command = String.format("ping -c 1 -W 1 -t %d %s", hop, targetIp);
        
        currentCount = 0;
        
        List<SingleNodeResult> singleNodeList = new ArrayList<>();
        while ((isRunning = !Thread.currentThread().isInterrupted()) && (currentCount < count)) {
            try {
                long startTime = SystemClock.elapsedRealtime();
                SingleNodeResult nodeResult = parseSingleNodeInfoInput(execCommand(command));
                float delay = (SystemClock.elapsedRealtime() - startTime) / 2.f;
                JLog.T(TAG, String.format("[thread]:%d, [trace singleNode]:%s", Thread.currentThread().getId(), nodeResult.toString()));
                if (!nodeResult.isFinalRoute()
                        && nodeResult.getStatus() == UCommandStatus.CMD_STATUS_SUCCESSFUL)
                    nodeResult.setDelay(delay);
                
                singleNodeList.add(nodeResult);
            } catch (IOException | InterruptedException e) {
                JLog.I(TAG, String.format("traceroute[%d]: %s occur error: %s", currentCount, command, e.getMessage()));
            } finally {
                currentCount++;
            }
        }
        
        resultData = new TracerouteNodeResult(targetAddress.getHostAddress(), hop, singleNodeList);
        if (callback != null)
            callback.onTracerouteNode(resultData);
    
        return isRunning ? resultData : null;
    }
    
    protected SingleNodeResult parseSingleNodeInfoInput(String input) {
        JLog.T(TAG, "[hop]:" + hop + " [org data]:" + input);
        SingleNodeResult nodeResult = new SingleNodeResult(targetAddress.getHostAddress(), hop);
        
        if (TextUtils.isEmpty(input)) {
            nodeResult.setStatus(UCommandStatus.CMD_STATUS_NETWORK_ERROR);
            nodeResult.setDelay(0.f);
            return nodeResult;
        }
        
        Matcher matcherRouteNode = matcherRouteNode(input);
        
        if (matcherRouteNode.find()) {
            nodeResult.setRouteIp(getIpFromMatcher(matcherRouteNode));
            nodeResult.setStatus(UCommandStatus.CMD_STATUS_SUCCESSFUL);
        } else {
            Matcher matcherTargetId = matcherIp(input);
            if (matcherTargetId.find()) {
                nodeResult.setRouteIp(matcherTargetId.group());
                nodeResult.setStatus(UCommandStatus.CMD_STATUS_SUCCESSFUL);
                String time = getPingDelayFromMatcher(matcherTime(input));
                nodeResult.setDelay(Float.parseFloat(time));
            } else {
                nodeResult.setStatus(UCommandStatus.CMD_STATUS_FAILED);
                nodeResult.setDelay(0.f);
            }
        }
        
        return nodeResult;
    }
    
    @Override
    protected void parseInputInfo(String input) {
    
    }
    
    @Override
    protected void parseErrorInfo(String error) {
        if (!TextUtils.isEmpty(error))
            JLog.T(TAG, "[hop]:" + hop + " [error data]:" + error);
        
    }
    
    @Override
    protected void stop() {
        isRunning = false;
    }
}
