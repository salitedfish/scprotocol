package com.labdatahub.protocol.deal;

import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;
import com.labdatahub.protocol.parent.HttpServerProtocol;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description: HTTP消息处理
 * @Author: labdatahub
 * @CreateTime: 2025-09-17
 */
public class HttpServerDeal implements HttpServerProtocol {

//    @Override
//    public DecodeMessage decode(String requestMethod, String requestPath, String contentType, Map<String, String> headers, Map<String, String> queryParams, String requestBody, Map<String, String> formData) throws Exception {
//        DecodeMessage decodeMessage = new DecodeMessage();
//        JSONObject data = JSONObject.parseObject(requestBody);
//        decodeMessage.setDeviceSn(data.getString("deviceSn"));
//        decodeMessage.setReportTime(new Date());
//        Map<String,Object> map = new HashMap<>();
//        data.keySet().forEach(key->{
//            map.put(key,data.get(key));
//        });
//        decodeMessage.setProperties(map);
//        decodeMessage.setHttpNeedReply(false);
//        decodeMessage.setHttpReply(requestBody);
//        return decodeMessage;
//    }

	@Override
    public DecodeMessage decode(JSONObject objecotData) throws Exception {
        DecodeMessage decodeMessage = new DecodeMessage();
        JSONObject data = JSONObject.parseObject(objecotData.getString("requestBody"));
        decodeMessage.setDeviceSn(data.getString("deviceSn"));
        decodeMessage.setReportTime(new Date());
        Map<String,Object> map = new HashMap<>();
        data.keySet().forEach(key->{
            map.put(key,data.get(key));
        });
        decodeMessage.setProperties(map);
        decodeMessage.setHttpNeedReply(false);
        decodeMessage.setHttpReply(objecotData.getString("requestBody"));
        return decodeMessage;
    }
    @Override
    public EncodeMessage encode(String functionCode, String deviceSn, Map<String,Object> properties, String params,String customConfig,Map<String,Object> otherConfig) throws Exception {
        return new EncodeMessage();
    }
}
