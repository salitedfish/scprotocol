package com.labdatahub.protocol.parent;

import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;

import java.util.List;
import java.util.Map;

/**
 * COAP服务端协议包必须实现的统一接口。
 */
public interface CoapServerProtocol {

//    /**
//     * 解析设备上传的数据
//     * @param requestMethod 请求方式POST、PUT、GET、DELETE等
//     * @param requestPath 资源路径
//     * @param pathParams 路径参数
//     * @param payload 数据
//     * @return 解析后的标准化数据，必须是DecodeMessage类
//     * @throws Exception 解析异常
//     */
//    DecodeMessage decode(String requestMethod, String requestPath, String payload, List<String> pathParams) throws Exception;
    
    DecodeMessage decode(JSONObject objecotData) throws Exception;

    /**
     * 功能下行指令（平台向设备发送数据）
     *
     * @param functionCode 功能编码
     * @param deviceSn     设备标识
     * @param properties   设备最新全属性状态
     * @param params       用户自定义参数
     * @param clientIp     设备上报数据时的IP
     * @param clientPort   设备上报数据时的端口
     * @param customConfig 设备自定义配置
     * @return 需要下发的配置信息
     * @throws Exception 编码异常
     */
    EncodeMessage encode(String functionCode, String deviceSn, Map<String, Object> properties, String params,String customConfig,Map<String,Object> otherConfig) throws Exception;
}
