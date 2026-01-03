1. MainActivity.java

專案入口頁

負責選擇模式（平面 / 斜面 / 會員）

將選擇的模式透過 Intent 傳給 SimulationActivity

2. SimulationActivity.java（核心控制邏輯）
功能總覽

語音辨識（SpeechRecognizer）

模式切換與初始化

物理運動計算（速度、加速度、重力、摩擦）

會員專屬設定（SeekBar）

模式說明

mode = 0（平面模式）

無重力

無摩擦力

使用 background_flat

不顯示斜坡

mode = 1（一般斜面模式）

斜面角度固定 45°

有重力分量（沿斜面）

無摩擦力

使用 background_slope

mode = 2（會員專屬模式）

使用者可自訂：

球的質量（mass）

斜面角度（theta）

摩擦係數（mu）

物理計算依照使用者設定即時更新

語音控制

辨識關鍵字：「加速」、「跑」、「快」

每喊一次加速，會施加一次「力」

使用公式 a = F / m 轉換成加速度

物理計算核心

重力沿斜面分量：g * sin(theta)

摩擦力：mu * g * cos(theta)

使用時間步長 DT 模擬連續運動

速度不可小於 0

3. SimulationView.java（畫面呈現）
功能總覽

負責所有畫面繪製

背景水平捲動，模擬前進感

球體固定於畫面中央

視覺與物理邏輯分離

背景系統

支援兩種背景：

background_flat：平面模式

background_slope：斜面與會員模式

依模式動態切換背景並保持比例縮放

斜坡顯示

斜坡圖為去背 overlay（road_overlay.png）

只在斜面與會員模式顯示

平面模式不顯示斜坡

球體顯示

球在斜面模式下會略微旋轉（視覺抬頭）

平面模式保持水平

球的位置會依是否顯示斜坡而調整

會員專屬功能說明

在會員模式下，畫面下方會顯示三條 SeekBar：

質量（Mass, kg）

影響加速度大小（a = F / m）

質量越大，加速效果越小

斜面角度（Theta, degree）

影響重力沿斜面分量

角度越大，越難向上移動

摩擦係數（Mu）

影響摩擦減速效果

僅會員模式啟用
