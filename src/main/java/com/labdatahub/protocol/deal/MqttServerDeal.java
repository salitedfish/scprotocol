package com.labdatahub.protocol.deal;

import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;
import com.labdatahub.protocol.parent.MqttBrokerProtocol;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description: MQTT_SERVER消息处理
 * @Author: labdatahub
 * @CreateTime: 2025-09-17
 */
public class MqttServerDeal implements MqttBrokerProtocol {

//    @Override
//    public DecodeMessage decode(String topic,String message) throws Exception {
//        DecodeMessage decodeMessage = new DecodeMessage();
//        JSONObject data = JSONObject.parseObject(message);
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
		String topic = objecotData.getString("topic");
		String message = objecotData.getString("message");
        DecodeMessage decodeMessage = new DecodeMessage();
        JSONObject data = JSONObject.parseObject(message);
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
        return EncodeMessage.builder()
                .content(params.getBytes(StandardCharsets.UTF_8))
                .deviceSn(deviceSn)
                .qos(0)
                .isSend(true)
                .topic("/test/t1")
                .build();
    }
}
