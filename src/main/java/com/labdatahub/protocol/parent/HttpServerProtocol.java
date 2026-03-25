package com.labdatahub.protocol.parent;

import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;

import java.util.Map;

/**
 * UDP协议包必须实现的统一接口。
 */
public interface HttpServerProtocol {

//    /**
//     * 解析设备消息
//     *
//     * @param requestMethod 请求方式 GET、POST、PUT、DELETE、PATCH 等
//     * @param requestPath   请求路径，如：/api/getList 等
//     * @param contentType   body类型，如：multipart/form-data、application/json、application/x-www-form-urlencoded、raw 等
//     * @param headers       请求头MAP集合
//     * @param queryParams   路径参数，如/api/getList?param1=xxx&param2=xxx中的param1和param2
//     * @param requestBody   请求体，主要用于contentType为application/json和raw时，默认是String类型，你可以自行转换为json和其它类型
//     * @param formData      表单类型，当contentType为multipart/form-data和application/x-www-form-urlencoded时，该参数有值
//     * @return 解析后上报到平台的消息
//     * @throws Exception
//     */
//    DecodeMessage decode(String requestMethod,
//                         String requestPath,
//                         String contentType,
//                         Map<String, String> headers,
//                         Map<String, String> queryParams,
//                         String requestBody,
//                         Map<String, String> formData) throws Exception;	
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
