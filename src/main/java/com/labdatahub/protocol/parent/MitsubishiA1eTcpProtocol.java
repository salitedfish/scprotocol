//由AI修改
package com.labdatahub.protocol.parent;

import java.util.Map;

import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;

/**
 * 三菱 MC 协议（MITSUBISHI_A1E_TCP，A 兼容 1E 帧）必须实现的统一接口。
 * 方法签名必须与 api 侧 ProtocolManager 反射路径一致：decode(JSONObject) / encode(String,String,Map,String,String,Map)。
 *
 * <p><b>与 MC1E / MC3E 的关键区别</b>：A 兼容 1E 帧面向 **A 系列 PLC（AnA/AnU/AnS）**，
 * 它与 QnA 系（1E/3E）**不是同一套命令集，也不是同一张软元件码表** ——
 * QnA 系用二进制软元件码（D=0xA8、M=0x90…），A 系用的是另一套编码，
 * 且部分命令的功能码编号不同。所以本项目里三菱的三种帧是三个**互不依赖**的 netType，
 * 不复用彼此的软元件码表与命令常量。
 *
 * <p><b>⚠️ 本协议只有文件、没有实现，当前完全不可用</b>：
 * <ul>
 *   <li>{@code decode} 可用（帧无关的值转换，与 3E/1E 同源）；</li>
 *   <li>{@code encode} <b>显式抛 {@link UnsupportedOperationException}</b> ——
 *       A 系软元件码表未确认，照着 QnA 的码表写只会静默写错地方，宁可抛错。</li>
 *   <li>api 侧帧层（{@code mitsubishi_a1e_tcp} 组件包）**不存在**，平台侧接线（NetType /
 *       MAIN_CLASS_PATH / 建表 / 前端下拉与配置 tab）**一处都没加**，因此本协议目前
 *       既上传不了也选不到。</li>
 * </ul>
 *
 * <p>真机到位后的补全顺序见 {@link com.labdatahub.protocol.deal.MitsubishiA1eTcpDeal} 的类注释。
 */
public interface MitsubishiA1eTcpProtocol {

    /**
     * 解析设备寄存器的数据
     * @param objecotData 读取数据（含 deviceSn/code/jsonArray/dataType/byteOrder/isSigned/scale/offset）
     * @return 解析后的标准化数据，必须是DecodeMessage类
     * @throws Exception 解析异常
     */
    DecodeMessage decode(JSONObject objecotData) throws Exception;

    /**
     * 功能下行指令（平台向设备发送数据）
     *
     * @param functionCode 功能编码
     * @param deviceSn     设备标识
     * @param properties   设备最新全属性状态
     * @param params       用户自定义参数
     * @param customConfig 设备自定义配置
     * @return 需要下发的配置信息
     * @throws Exception 编码异常
     */
    EncodeMessage encode(String functionCode, String deviceSn, Map<String, Object> properties, String params, String customConfig, Map<String, Object> otherConfig) throws Exception;
}
