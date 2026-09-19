//由AI修改
package com.labdatahub.protocol.deal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.labdatahub.protocol.message.DecodeMessage;
import com.labdatahub.protocol.message.EncodeMessage;
import com.labdatahub.protocol.parent.MitsubishiA1eTcpProtocol;
import com.labdatahub.protocol.utils.RegisterValueUtils;

/**
 *
* @ClassName: MitsubishiA1eTcpDeal
* @Description: 三菱 MC 协议消息处理（A 兼容 1E 帧，A 系列 PLC AnA/AnU/AnS）
* @author xwb
* @date 2026年9月19日
*
* <p><b>本类是骨架，不是实现</b>：只建立协议文件，等真机对接后再写。当前状态 ——
* <ul>
*   <li>{@code decode}：可用。它只做「api 侧读出的原始字块 → 按物模型转业务值」，
*       与帧型无关（帧头、长度字段在那之前已被 api 侧剥掉），所以能照着 3E/1E 那份直接写。</li>
*   <li>{@code encode}：<b>显式抛 {@link UnsupportedOperationException}</b>，见下方说明。</li>
* </ul>
*
* <p><b>⚠️ 为什么 encode 宁可抛错也不照抄 QnA 那份</b>：写方向要按<b>软元件码</b>分字设备/位设备，
* 而 A 系（A 兼容 1E 帧）的软元件码表与 QnA 系（1E/3E）<b>不是同一张表</b>，
* 命令功能码也有部分不同。照抄后果是**静默写错地方**——不报错、也不写到你指定的软元件，
* 这种错在现场极难定位。所以 A 系软元件码表未确认之前，一律拒绝写入。
* 读方向不受影响（读的是 api 侧按点位配置取回的原始值，不经这张表）。
*
* <p><b>真机到位后的补全顺序</b>：
* <ol>
*   <li>先确认 A 系帧格式与软元件码表（三菱 A 系列 PLC 手册的「A 兼容 1E 帧」章节，
*       或抓现有上位机报文核对）。**这一步没做完，下面的都不要动** —— 帧格式错、码表错都是静默错误。</li>
*   <li>api 侧建 {@code mitsubishi_a1e_tcp} 组件包（参照 {@code mitsubishi_mc3e_tcp} 十件套），
*       按 A 兼容 1E 帧组帧；协议库不含网络 I/O，组帧全在这一层。</li>
*   <li>把本类的 encode 补齐（字/位分叉 + 软元件码表 + 载荷键名与 api 侧 PointWriter 对齐），
*       同时删掉接口注释里「当前不可用」那段。</li>
*   <li>平台接线：{@code NetType} 枚举、{@code MAIN_CLASS_PATH}、
*       {@code LabdatahubComponentServiceImpl} open/close case、{@code TimerTask} 起调度、
*       建表脚本、前端协议/组件页下拉 + 设备与产品详情页配置 tab、
*       {@code PointWriteMetaResolver} 准入白名单与 {@code PointWriter} 分派。</li>
* </ol>
 */
public class MitsubishiA1eTcpDeal implements MitsubishiA1eTcpProtocol {

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

    /**
     * 写值方向编码：<b>未实现，直接抛错</b>。
     *
     * <p>不返回 {@code isSend=false} 的只读 stub —— 那种写法在本平台语义里等于「写成功」，
     * 会让调用方以为值已经下去了。这里是真的没实现，必须让调用方立刻知道。
     * 补全所需的步骤见类注释。
     *
     * @throws UnsupportedOperationException 恒抛（A-1E 帧待真机对接后实现）
     */
    @Override
    public EncodeMessage encode(String functionCode, String deviceSn, Map<String, Object> properties,
                                String params, String customConfig, Map<String, Object> otherConfig) throws Exception {
        throw new UnsupportedOperationException("A-1E 帧待真机对接后实现："
                + "A 兼容 1E 帧的软元件码表与命令集和 QnA 系（1E/3E）不同，"
                + "照抄 QnA 的码表会静默写错软元件，故在确认 A 系帧格式前拒绝写入。"
                + "设备：" + deviceSn);
    }
}
