# 非调试预览构建准备

本页描述显式的本地非调试预览路径。脚本和CI配置已准备，不表示本次提交已构建、真机验收、正式发行或替换官网下载。原生鸿蒙能力也不由构建模式改变。

## 备用SDK构建模式

`tools/build-local.ps1`默认仍是`Debug`，行为兼容旧命令：Manifest设置`android:debuggable=true`，输出`SplashGuard-版本-preview.apk`，必要时在输出目录创建本地开发密钥。这个方便贡献者试验的默认路径不用于正式发行；不同输出目录可能产生不同证书，不能据此覆盖已有用户安装。

新增`-BuildMode NonDebugPreview`，要求显式指定已经存在的密钥库、别名、密码环境变量名和维护者核对过的SHA-256证书指纹。脚本在构建前导出所选公有证书并比较指纹，未提供或不匹配即停止；不会为这个模式新建密钥或回退默认开发签名。别名不存在、凭据不可用或签名工具失败同样终止。签名后还会对最终APK验签，并核对唯一签名者的实际证书指纹与期望一致；只读取密钥库中的证书不算最终APK已经符合要求。

该模式将最终Manifest明确设置`android:debuggable=false`，默认输出目录为`build/nondebug-preview/`，产物名称为`SplashGuard-版本-nondebug-preview.apk`。名称与调试包分开；它仍是由SDK备用工具链生成的预览包，不冒充AGP正式发行流程。两种模式均保留现有编译、资源、D8、对齐、验签和纯Java检查。

## 无密钥编译验证：UnsignedValidation

`-BuildMode UnsignedValidation`提供不接触签名材料的独立验证路径。该模式拒绝所有显式签名参数（即使参数值为空），不读取密码环境变量、不读取或创建密钥库、不执行签名工具的sign或verify命令。它只编译资源、Java与D8、打包并zipalign，运行现有纯Java检查，将对齐后的未签名文件保存为`SplashGuard-版本-unsigned-validation.apk`；Manifest明确`android:debuggable=false`，默认目录为`build/unsigned-validation/`。

**此产物未签名，不可作为安装包安装、发布或替换用户下载。** 它只能证明本次源码的备用SDK编译路径可走通，不能证明签名、升级、手机运行或广告效果。不能用这个模式绕过任何被拒绝的签名操作，也不能随后擅自补签。

```powershell
./tools/build-local.ps1 `
    -SdkRoot 'C:/Android/Sdk' `
    -JdkRoot 'C:/Java/jdk-21' `
    -OutDir './build/unsigned-validation' `
    -BuildMode UnsignedValidation
```

这条命令不需要、也不接受任何SigningKeyStore、SigningKeyAlias、StorePassEnv、KeyPassEnv或ExpectedCertificateSha256参数。默认Debug和显式NonDebugPreview仍分别遵循原有行为；仅靠模式名称不能宣称此路径已经运行成功，需实际执行结果。

## 维护者调用

以下示例适用于PowerShell7；所有密钥路径、别名及证书指纹必须由维护者事先核对。密码通过遮蔽输入进入本次进程环境，脚本只接收环境变量名称，`apksigner`从环境读取，不把密码值放入命令参数或脚本日志。不要把实际密码粘贴到命令历史、源码、工单或截图。

```powershell
$env:SPLASHGUARD_STORE_PASS = Read-Host '密钥库密码' -MaskInput
$env:SPLASHGUARD_KEY_PASS = Read-Host '签名条目密码' -MaskInput
try {
    ./tools/build-local.ps1 `
        -SdkRoot 'C:/Android/Sdk' `
        -JdkRoot 'C:/Java/jdk-21' `
        -OutDir './build/nondebug-preview' `
        -BuildMode NonDebugPreview `
        -SigningKeyStore 'C:/Private/SplashGuard/upgrade-signing.keystore' `
        -SigningKeyAlias '维护者实际别名' `
        -StorePassEnv 'SPLASHGUARD_STORE_PASS' `
        -KeyPassEnv 'SPLASHGUARD_KEY_PASS' `
        -ExpectedCertificateSha256 '维护者核对过的64位十六进制指纹'
} finally {
    Remove-Item Env:/SPLASHGUARD_STORE_PASS -ErrorAction SilentlyContinue
    Remove-Item Env:/SPLASHGUARD_KEY_PASS -ErrorAction SilentlyContinue
}
```

检查最终APK哈希、版本、Manifest中的调试标记与实际签名证书，再由用户本人通过正常系统安装验证更新。证书连续性、关闭调试、构建通过，均不代替广告效果、多机型、文件选择器、长期后台或应用市场发布验收。

保持既有升级签名需要妥善保存原有私钥。把APK设为不可调试并不会自动将原开发证书升级为新的正式签名治理方案；密钥备份、访问控制、发行责任与未来轮换仍应由维护者单独管理。项目忽略本地密钥和输出目录，提交前仍需检查文件清单。

## 标准Gradle CI

独立Android工作流新增执行`assembleRelease`与`lintRelease`，同时保留`assembleDebug`和`lintDebug`。默认AGP release没有配置签名，因此此处是未签名release构建验证；CI不读取维护者密钥、不给release套调试证书、不上传APK、不发布或自动合并。所有lint任务按默认严重级别执行，不添加基线或忽略错误。

当这些任务实际执行成功后，需在验证记录中附准确源码SHA及检查链接；新增工作流命令本身不算成功。SDK备用非调试包与CI的未签名AGP release也应分别记录，不能混作同一产物。

本地0.1.12/code13的UnsignedValidation实际构建与862项纯检查通过，Manifest不可调试、无uses-permission；验证文件115367字节，SHA256 cf2a8e5ea2ad3219b9681de6c246953824c2dc36b1075703a4c65da80ad27062。此记录不包含签名、安装或正式发行，仍不可安装／发布。

源码`26f63c5a0d7d2c6f1bb8708fedea1c4be230d45d`的[标准Gradle检查](https://github.com/zhengxiuyuanustc-cmd/SplashGuard/actions/runs/40331782330)也已实际成功，包含debug/未签名release和各自lint。它不含维护者私钥，也不改变当前本地可安装包尚未签名的状态。
