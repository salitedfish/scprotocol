//由AI修改
package com.labdatahub.protocol.deal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;
import com.labdatahub.protocol.parent.MitsubishiTcpProtocol;
import com.labdatahub.protocol.utils.RegisterValueUtils;

/**
 *
* @ClassName: MitsubishiTcpDeal
* @Description: 三菱 MC 协议消息处理（QnA兼容3E帧，decode 与 FINS 同构：jsonArray 扁平字块 → RegisterValueUtils.convert）
* @author xwb
* @date 2026年8月24日
 */
public class MitsubishiTcpDeal implements MitsubishiTcpProtocol {

	@Override
    public DecodeMessage decode(JSONObject objecotData) throws Exception {
		String code = objecotData.getString("code");
		String deviceSn = objecotData.getString("deviceSn");
		String dataType = objecotData.getString("dataType");
		String byteOrder = objecotData.getString("byteOrder");
		String isSigned = objecotData.getString("isSigned");
		Double scale = objecotData.getDouble("scale");
		Double offset = objecotData.getDouble("offset");
        DecodeMessage decodeMessage = new DecodeMessage();
        decodeMessage.setDeviceSn(deviceSn);
        Map<String,Object> properties = new HashMap<>();
        JSONArray jsonArray = objecotData.getJSONArray("jsonArray");
        if (jsonArray != null) {
            List<Integer> data = JSONArray.parseArray(jsonArray.toJSONString(), Integer.class);
            Object value = RegisterValueUtils.convert(data, dataType, byteOrder, isSigned, scale, offset);
            properties.put(code, value);
        }
        decodeMessage.setProperties(properties);
        //解析消息
        return decodeMessage;
    }

    @Override
    public EncodeMessage encode(String functionCode, String deviceSn, Map<String, Object> properties, String params, String customConfig, Map<String, Object> otherConfig) throws Exception {
        // 三菱 MC 协议写暂未实现，返回只读 stub（isSend=false）
        return EncodeMessage.builder()
                .isSend(false)
                .deviceSn(deviceSn)
                .build();
    }
}
