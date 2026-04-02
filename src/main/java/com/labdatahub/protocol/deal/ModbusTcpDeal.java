package com.labdatahub.protocol.deal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;
import com.labdatahub.protocol.message.RangeItem;
import com.labdatahub.protocol.parent.ModbusTcpProtocol;

/**
 * @Description: UDP消息处理
 * @Author: labdatahub
 * @CreateTime: 2025-09-17
 */
public class ModbusTcpDeal implements ModbusTcpProtocol {

//    @Override
//    public DecodeMessage decode(String deviceSn, Integer slaveId, String code, String registerRange, JSONArray jsonArray) throws Exception {
//        List<RangeItem> itemList = JSONArray.parseArray(jsonArray.toJSONString(),RangeItem.class);
//        DecodeMessage decodeMessage = new DecodeMessage();
//        decodeMessage.setDeviceSn(deviceSn);
//        Map<String,Object> properties = new HashMap<>();
//        if(itemList.size()>0){
//            for (int i = 0; i < itemList.size(); i++) {
//                RangeItem rangeItem = itemList.get(i);
//                List<Integer> data = rangeItem.getRegisterList();
//                properties.put("temperature",data.get(0));
//                properties.put("windSpeed",data.get(1));
//            }
//        }
//        decodeMessage.setProperties(properties);
//        //解析消息
//        return decodeMessage;
//    }
	
	public DecodeMessage decode(JSONObject objecotData) throws Exception {
		String code = objecotData.getString("code");
		String deviceSn = objecotData.getString("deviceSn");
		JSONArray jsonArray = objecotData.getJSONArray("jsonArray");
		List<RangeItem> itemList = JSONArray
				.parseArray(jsonArray.toJSONString(new com.alibaba.fastjson2.JSONWriter.Feature[0]), RangeItem.class);
		DecodeMessage decodeMessage = new DecodeMessage();
		decodeMessage.setDeviceSn(deviceSn);
		Map<String, Object> properties = new HashMap<>();
		if (itemList.size() > 0)
			for (int i = 0; i < itemList.size(); i++) {
				RangeItem rangeItem = itemList.get(i);
				List<Integer> data = rangeItem.getRegisterList();
				if (data.size() > 1) {
					int reg0 = ((Integer) data.get(0)).intValue();
					int reg1 = ((Integer) data.get(1)).intValue();
					int combined = reg0 << 16 | reg1 & 0xFFFF;
					float result = Float.intBitsToFloat(combined);
					properties.put(code, Float.valueOf(result));
				} else {
					properties.put(code, data.get(0));
				}
			}
		decodeMessage.setProperties(properties);
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
