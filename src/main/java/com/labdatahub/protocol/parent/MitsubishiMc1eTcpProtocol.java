//由AI修改
package com.labdatahub.protocol.parent;

import java.util.Map;

import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;

/**
 * 三菱 MC 协议（MITSUBISHI_MC1E_TCP，QnA 系列 1E 帧）必须实现的统一接口。
 * 方法签名必须与 api 侧 ProtocolManager 反射路径一致：decode(JSONObject) / encode(String,String,Map,String,String,Map)。
 *
 * <p><b>与 3E 的关系（2026-09-19 拆分）</b>：三菱 PLC 的 MC 协议按帧型分三种，原本挤在一个
 * {@code MITSUBISHI_MC3E_TCP} 里用配置列 {@code protocol_mode} 分叉，现已按「完全当做单独协议」
 * 拆成三个互不依赖的 netType（各自单独编译上传、各自注册 mainClass）：
 * <table border="1">
 *   <tr><th>netType</th><th>帧型</th><th>适用机型</th><th>状态</th></tr>
 *   <tr><td>{@code MITSUBISHI_MC3E_TCP}</td><td>QnA 兼容 3E 帧（21 字节请求）</td><td>Q / QnA / iQ-R / iQ-F</td><td>已实装并验收</td></tr>
 *   <tr><td>{@code MITSUBISHI_MC1E_TCP}</td><td>QnA 系列 1E 帧（19 字节请求）</td><td>QnA 系列老机型</td><td><b>协议库本层已就绪，api 侧帧层待真机对接后实现</b></td></tr>
 *   <tr><td>{@code MITSUBISHI_A1E_TCP}</td><td>A 兼容 1E 帧</td><td>A 系列（AnA/AnU/AnS）</td><td><b>仅建立协议文件，待真机对接后实现</b></td></tr>
 * </table>
 *
 * <p><b>⚠️ 本协议当前不可用，勿在网络组件页选用</b> —— 本文件只是把协议文件建立起来，还缺两样东西：
 * <ol>
 *   <li><b>api 侧帧层</b>：网络 I/O 与组帧在 {@code lab-data-hub-api} 的
 *       {@code labdatahub-component/.../mitsubishi_mc1e_tcp} 组件包（参照 {@code mitsubishi_mc3e_tcp} 的
 *       DataReader / ConnectionManager / Scheduler / LoopConsumer / ConsumeService 十件套），
 *       该类包**尚不存在** —— 协议库只做纯编解码、不含网络 I/O 也不感知软元件寻址，所以真正读不出数据的是这一层。</li>
 *   <li><b>平台侧接线</b>：{@code NetType} 枚举、{@code LabdatahubProtocolServiceImpl.MAIN_CLASS_PATH}、
 *       {@code LabdatahubComponentServiceImpl} 的 open/close case、{@code TimerTask#initMitsubishiMc1eTcpRead}、
 *       建表脚本、协议/组件/设备详情页三处前端下拉与配置 tab —— 一处都还没加，
 *       <b>因此本协议目前既上传不了也选不到，不会以「能选但连不上」的形态误导人</b>。</li>
 * </ol>
 *
 * <p>真机到位后的补全顺序见 {@link com.labdatahub.protocol.deal.MitsubishiMc1eTcpDeal} 的类注释。
 */
public interface MitsubishiMc1eTcpProtocol {

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
