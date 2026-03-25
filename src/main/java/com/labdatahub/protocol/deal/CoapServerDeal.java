package com.labdatahub.protocol.deal;

import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;
import com.labdatahub.protocol.parent.CoapServerProtocol;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: COAP消息处理
 * @Author: labdatahub
 * @CreateTime: 2025-10-15
 */
public class CoapServerDeal implements CoapServerProtocol {

//    @Override
//    public DecodeMessage decode(String requestMethod, String requestPath, String payload, List<String> pathParams) throws Exception {
//        DecodeMessage decodeMessage = new DecodeMessage();
//        JSONObject data = JSONObject.parseObject(payload);
//        decodeMessage.setDeviceSn(data.getString("deviceSn"));
//        decodeMessage.setReportTime(new Date());
//        Map<String,Object> map = new HashMap<>();
//        data.keySet().forEach(key->{
//            map.put(key,data.get(key));
//        });
//        decodeMessage.setProperties(map);
//        return decodeMessage;
//    }
    
    @Override
    public DecodeMessage decode(JSONObject objecotData) throws Exception {
    	String payload = objecotData.getString("payload");
    	//List<String> pathParams = objecotData.getList("pathParams", String.class);
        DecodeMessage decodeMessage = new DecodeMessage();
        JSONObject data = JSONObject.parseObject(payload);
        decodeMessage.setDeviceSn(data.getString("deviceSn"));
        decodeMessage.setReportTime(new Date());
        Map<String,Object> map = new HashMap<>();
        data.keySet().forEach(key->{
            map.put(key,data.get(key));
        });
        decodeMessage.setProperties(map);
        return decodeMessage;
    }

    @Override
    public EncodeMessage encode(String functionCode, String deviceSn, Map<String,Object> properties, String params,String customConfig,Map<String,Object> otherConfig) throws Exception {
        return new EncodeMessage();
    }
}
