//由AI修改
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
import com.labdatahub.protocol.utils.RegisterValueUtils;

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
		String dataType = objecotData.getString("dataType");
		String byteOrder = objecotData.getString("byteOrder");
		String isSigned = objecotData.getString("isSigned");
		Double scale = objecotData.getDouble("scale");
		Double offset = objecotData.getDouble("offset");
		JSONArray jsonArray = objecotData.getJSONArray("jsonArray");
		List<RangeItem> itemList = JSONArray
				.parseArray(jsonArray.toJSONString(), RangeItem.class);
		DecodeMessage decodeMessage = new DecodeMessage();
		decodeMessage.setDeviceSn(deviceSn);
		Map<String, Object> properties = new HashMap<>();
		if (itemList != null)
			for (int i = 0; i < itemList.size(); i++) {
				RangeItem rangeItem = itemList.get(i);
				List<Integer> data = rangeItem.getRegisterList();
				Object value = RegisterValueUtils.convert(data, dataType, byteOrder, isSigned, scale, offset);
				properties.put(code, value);
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
