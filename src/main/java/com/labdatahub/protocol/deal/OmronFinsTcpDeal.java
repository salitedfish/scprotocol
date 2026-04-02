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
import com.labdatahub.protocol.parent.OmronFinsTcpProtocol;

/**
 * 
* @ClassName: OmronFinsTcpDeal  
* @Description: OmronFinsTcp消息处理
* @author xwb  
* @date 2026年4月1日
 */
public class OmronFinsTcpDeal implements OmronFinsTcpProtocol {

	@Override
    public DecodeMessage decode(JSONObject objecotData) throws Exception {
		String code = objecotData.getString("code");
		String deviceSn = objecotData.getString("deviceSn");
        DecodeMessage decodeMessage = new DecodeMessage();
        decodeMessage.setDeviceSn(deviceSn);
        Map<String,Object> properties = new HashMap<>();
        properties.put(code,objecotData.get("data"));
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
