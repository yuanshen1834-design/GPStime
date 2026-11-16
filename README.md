# 极光时钟 · GPStime

一个只做两件事的 Android 小工具：**用 GPS 卫星信号授时**，以及**看天气**。

[下载 APK](https://github.com/yuanshen1834-design/GPStime/releases/latest) · 最低支持 Android 7.0（API 24）

## 功能

### GPS 授时
- 读取 `GPS_PROVIDER` 的位置时间戳，显示卫星时间（与本机时区同步）
- 显示时区、日期、时间
- 显示经纬度、海拔、定位精度
- 显示卫星总数、参与定位数，以及 GPS / GLONASS / Galileo / BeiDou 各系统卫星数

### 网络时间对比
- 读取 HTTP 响应头的 `Date`，得到网络时间
- 与 GPS 时间做差，按差值大小用绿/橙/红提示

### 天气（和风天气 QWeather）
- 当前温度、体感、湿度、风向风力
- 今日 + 明天 + 后天：天气、最高/最低温、降水量
- 打开定位后自动查询；也可手动“换城市”
- 定位精度达到 100 米内才查询；室内等待 10 秒后放宽到 3 公里，避免定到隔壁城市
- 用和风 GeoAPI 反查，城市名显示到区县（如“河南省 郑州市 金水区”）

## 定位与隐私
- 定位权限只用于 GPS 授时和当地天气，不会上传到任何自建服务器
- 查询天气时，只把经纬度（或你输入的城市名）发给和风天气
- 首次启动会说明用途并请求权限；拒绝后仍可用“换城市”手动查天气

## 天气服务配置（必须）

本项目**不内置**任何 API Key。第一次使用时，在 App 里填入自己的和风天气凭据：

1. 打开 [和风天气控制台](https://console.qweather.com/) 注册并登录（需验证邮箱、绑定手机号）
2. 「项目管理」→ 创建项目，订阅选择**免费开发版**（约 1000 次/天）
3. 在项目中「创建凭据」，认证方式选 **API Key**，创建后复制 Key
4. 控制台「设置」→ **API Host**，复制形如 `abcd1234.re.qweatherapi.com` 的专属地址
5. 打开 App → 天气卡片右下角「设置」→ 填入 **API Host** 和 **API Key** → 保存

> 旧的 `api.qweather.com` / `devapi.qweather.com` 公共域名已不可用，必须使用账号专属的 API Host。

## 构建

环境：JDK 17+、Android SDK Platform 34、Gradle 8.10.2（已带 wrapper）。

```bash
git clone https://github.com/yuanshen1834-design/GPStime.git
cd GPStime
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

在 Android Studio 中直接打开项目也可以构建。

## 项目结构

```
app/src/main/java/com/gpstime/app/
├── MainActivity.kt          # 唯一界面：授时 + 天气
└── WeatherRepository.kt     # 和风天气接口、JSON 解析、天气图标映射
app/src/main/res/
├── layout/activity_main.xml
└── values/                  # 字符串、颜色、主题
app/src/test/java/.../WeatherParseTest.kt   # 天气 JSON 解析单元测试
```

技术栈：Kotlin、AndroidX、Material Components、Kotlin Coroutines、LocationManager、HttpURLConnection。

## 说明
- 个人自用的小工具，非官方应用，与和风天气无隶属关系
- 天气数据版权归和风天气所有，请遵守其服务条款
- 仓库暂未声明开源许可证；如需商用或二次分发，请先联系作者
