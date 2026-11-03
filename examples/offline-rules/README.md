# 离线作者签名演示（0.1.14候选）

这些文件用于开发者复核来源验签，不是日常必装规则或广告效果更新。0.1.14候选已完成本地构建及纯模型检查；系统文件选择器、旋转/进程重建、真实磁盘及广告效果仍待设备验收，不能用此示例替代。

| 文件 | 内容 |
| --- | --- |
| [community-v1.payload.json](community-v1.payload.json) | 作为签名输入的原始UTF-8 Pack JSON |
| [community-v1.jws](community-v1.jws) | 对上述原始字节生成的JWS Compact封装，供App“选择作者签名文件”使用 |

载荷为发布者`splashguard`、渠道`community`允许的`splashguard.community`版本1。它仅列出已有`generic.splash`的内置上限：12000毫秒、最多2轮、现有已选应用范围、禁止坐标触点。它是部分载荷，未列出的B站限制保持当前有效状态；不会新增匹配条件、应用、点击位置、权限或联网能力，也不限制正常方向传感器。

它可能在已收紧或停用通用规则的状态下被拒绝。首次确认虽然不为内置基线新增识别效果，仍会保存一次输入来源/版本及撤销记录。因此不建议普通用户为了“更好跳广告”而导入；内置规则可直接使用。签名只证明输入来源，不证明按钮真实或广告关闭。

原始载荷SHA-256：`614b92d3893281d2ac148120b2172814747640e7abb2c0caa619b26fd7d34915`。JWS文件SHA-256：`f766d8f7f94f599bc027afe397f5a8c1065ded9c03600562744fc2f9ba813bfb`。已核对JWS解码载荷与同目录payload文件逐字节相同；摘要针对原始字节，缩进或换行变化也会改变摘要，不应把重新格式化的文件当作相同版本重发。

验证公钥由App[RuleTrust](../../app/src/main/java/org/splashguard/app/RuleTrust.java)编译固定，公开配置见[rule-authority.json](../../tools/rule-authority.json)。独立规则私钥不在示例、App或仓库中，也未复用APK升级签名密钥。

自愿验证流程：在App设置进入“离线规则与诊断”，选择完整`.jws`文件，检查来源与限制预览；仅在准备改变规则状态时确认。再次导入相同版本和摘要只提示已接收，不重复应用，也不改变撤销记录；撤销或恢复内置不会清除可信渠道历史。更多边界见[使用与恢复说明](../../docs/OFFLINE_RULE_AUTH.md)、[协议设计](../../docs/OFFLINE_RULE_AUTH_DESIGN.md)。

作者工具为[sign-rule-pack.ps1](../../tools/sign-rule-pack.ps1)，要求Windows、PowerShell 7.6以上和本人已有的独立DPAPI封装规则私钥。开发者无需私钥即可检查示例和贡献源码；不要向他人索取或提交维护者私钥。签名工具输出仍须经过App的严格解析、历史版本和限制校验。
