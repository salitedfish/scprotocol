//由AI修改
package com.labdatahub.protocol.deal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;
import com.labdatahub.protocol.message.RangeItem;
import com.labdatahub.protocol.parent.S71200TcpProtocol;
import com.labdatahub.protocol.utils.RegisterValueUtils;

/**
 * 
* @ClassName: S71200TcpDeal  
* @Description: S71200Tcp消息处理
* @author xwb  
* @date 2026年3月24日
 */
public class S71200TcpDeal implements S71200TcpProtocol {

	@Override
    public DecodeMessage decode(JSONObject objecotData) throws Exception {
		String code = objecotData.getString("code");
		String deviceSn = objecotData.getString("deviceSn");
		String isSigned = objecotData.getString("isSigned");
		Double scale = objecotData.getDouble("scale");
		Double offset = objecotData.getDouble("offset");
        DecodeMessage decodeMessage = new DecodeMessage();
        decodeMessage.setDeviceSn(deviceSn);
        Map<String,Object> properties = new HashMap<>();
        // S7DataReader 对 DBW 按 16 位无符号读取，这里按物模型 isSigned 做符号扩展后再缩放
        Object value = objecotData.get("data");
        value = RegisterValueUtils.applySigned(value, isSigned);
        value = RegisterValueUtils.applyScale(value, scale, offset);
        properties.put(code,value);
        decodeMessage.setProperties(properties);
        //解析消息
        return decodeMessage;
    }
	
    @Override
    public EncodeMessage encode(String functionCode, String deviceSn, Map<String, Object> properties, String params, String customConfig,Map<String,Object> otherConfig) throws Exception {
        if("fun1".equals(functionCode)){
            List<RangeItem> list = new ArrayList<>();
            list.add(new RangeItem(0,2,Arrays.asList(22,4)));
            return EncodeMessage.builder()
                    .isSend(true)
                    .deviceSn(deviceSn)
                    .modbusWriteJson(JSONArray.toJSONString(list))
                    .build();
        }else {
            List<RangeItem> list = new ArrayList<>();
            list.add(new RangeItem(0,2,Arrays.asList(11,2)));
            return EncodeMessage.builder()
                    .isSend(true)
                    .deviceSn(deviceSn)
                    .modbusWriteJson(JSONArray.toJSONString(list))
                    .build();
        }
    }
}
