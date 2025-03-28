#include "git.h"

// 心率参数初始化
uint32_t aun_ir_buffer[500];  // IR LED sensor data
int32_t n_ir_buffer_length;   // data length
uint32_t aun_red_buffer[500]; // Red LED sensor data
int32_t n_sp02;               // SPO2 value
int8_t ch_spo2_valid;         // indicator to show if the SP02 calculation is valid
int32_t n_heart_rate;         // heart rate value
int8_t ch_hr_valid;           // indicator to show if the heart rate calculation is valid
uint8_t uch_dummy;
// variables to calculate the on-board LED brightness that reflects the heartbeats
uint32_t un_min, un_max, un_prev_data;
int i;
int32_t n_brightness;
float f_temp;
u8 temp_num = 0;
u8 temp[6];
char str[50];
u8 dis_hr = 0, dis_spo2 = 0;
static void Heart_num(void);
#define MAX_BRIGHTNESS 255

// 联网状态

// 软件定时器设定
static Timer task1_id;
static Timer task2_id;
static Timer task3_id;

// 获取全局变量
const char *topics[] = {S_TOPIC_NAME};

// 硬件初始化
void Hardware_Init(void)
{
   
    ADXL345_AUTO_Adjust((char *)&x, (char *)&y, (char *)&z); // 自动校准

    HC_SR04_IO2_Init(); // 超声波模块GPIO初始化
    TIM3_Init(7199, 0); // 以10KHz计数,定时100us

    sprintf((char *)str, "--- MAX30102 --");
    OLED_ShowCH(0, 2, (unsigned char *)str);
    max30102_init(); // 初始化max30102
    un_min = 0x3FFFF;
    un_max = 0;
    n_ir_buffer_length = 500; // buffer length of 100 stores 5 seconds of samples running at 100sps
    // read the first 500 samples, and determine the signal range
    for (i = 0; i < n_ir_buffer_length; i++) {
        while (MAX30102_INT == 1)
            ; // wait until the interrupt pin asserts

        max30102_FIFO_ReadBytes(REG_FIFO_DATA, temp);
        aun_red_buffer[i] = (long)((long)((long)temp[0] & 0x03) << 16) | (long)temp[1] << 8 | (long)temp[2]; // Combine values to get the actual number
        aun_ir_buffer[i] = (long)((long)((long)temp[3] & 0x03) << 16) | (long)temp[4] << 8 | (long)temp[5];  // Combine values to get the actual number

        if (un_min > aun_red_buffer[i])
            un_min = aun_red_buffer[i]; // update signal min
        if (un_max < aun_red_buffer[i])
            un_max = aun_red_buffer[i]; // update signal max
    }
    un_prev_data = aun_red_buffer[i];
    // calculate heart rate and SpO2 after first 500 samples (first 5 seconds of samples)
    maxim_heart_rate_and_oxygen_saturation(aun_ir_buffer, n_ir_buffer_length, aun_red_buffer, &n_sp02, &ch_spo2_valid, &n_heart_rate, &ch_hr_valid);

#if OLED // OLED文件存在
    OLED_Clear();
#endif
}
// 网络初始化
void Net_Init()
{

#if OLED // OLED文件存在
    char str[50];
    OLED_Clear();
    // 写OLED内容
    sprintf(str, "-请打开WIFI热点");
    OLED_ShowCH(0, 0, (unsigned char *)str);
    sprintf(str, "-名称:%s         ", SSID);
    OLED_ShowCH(0, 2, (unsigned char *)str);
    sprintf(str, "-密码:%s         ", PASS);
    OLED_ShowCH(0, 4, (unsigned char *)str);
    sprintf(str, "-频率: 2.4 GHz   ");
    OLED_ShowCH(0, 6, (unsigned char *)str);
#endif
    ESP8266_Init();          // 初始化ESP8266
    while (OneNet_DevLink()) // 接入OneNET
        delay_ms(300);
    while (OneNet_Subscribe(topics, 1)) // 订阅主题
        delay_ms(300);

    Connect_Net = 60; // 入网成功
#if OLED              // OLED文件存在
    OLED_Clear();
#endif
}

// 任务1
void task1(void)
{
  //定位成功，保存数据
	if (device_state_init.location_state == 1)
	{
		W_Test();
	}
}
// 任务2
void task2(void)
{
// 设备重连
#if NETWORK_CHAEK
    if (Connect_Net == 180) {
#if OLED // OLED文件存在
        OLED_Clear();
        // 写OLED内容
        sprintf(str, "-请打开WIFI热点");
        OLED_ShowCH(0, 0, (unsigned char *)str);
        sprintf(str, "-名称:%s         ", SSID);
        OLED_ShowCH(0, 2, (unsigned char *)str);
        sprintf(str, "-密码:%s         ", PASS);
        OLED_ShowCH(0, 4, (unsigned char *)str);
        sprintf(str, "-频率: 2.4 GHz   ");
        OLED_ShowCH(0, 6, (unsigned char *)str);
#endif
       
    }
#endif

    Read_Data(&Data_init);   // 更新传感器数据
    Update_oled_massage();   // 更新OLED
    Update_device_massage(); // 更新设备
                             // BEEP= ~BEEP;
    State = ~State;
}
// 任务3
void task3(void)
{
    // 10读一次
    Heart_num(); // 获取心率数据
    if (Connect_Net && Data_init.App == 0) {
        Data_init.App = 1;
    }
}
// 软件初始化
void SoftWare_Init(void)
{
    // 定时器初始化
    timer_init(&task1_id, task1, 50000, 1); // 每1分钟上传一次设备数据
    timer_init(&task2_id, task2, 50, 1);  // 跟新数据包
    timer_init(&task3_id, task3, 2500, 1); // 每10秒发送一次数据到客户端
 
}
// 主函数
int main(void)
{

    unsigned char *dataPtr = NULL;
    SoftWare_Init(); // 软件初始化
    Hardware_Init(); // 硬件初始化
    // 启动提示
    BEEP = 1;
   
    while (1) {

        // 线程
        timer_loop(); // 定时器执行
        // 串口接收判断
        dataPtr = ESP8266_GetIPD(0);
        if (dataPtr != NULL) {
            OneNet_RevPro(dataPtr); // 接收命令
        }
    }
}
/**********************************************************************
 * @ 函数名  ：获取心率数据
 ********************************************************************/
void Heart_num(void)
{
    i = 0;
    un_min = 0x3FFFF;
    un_max = 0;

    // dumping the first 100 sets of samples in the memory and shift the last 400 sets of samples to the top
    for (i = 100; i < 500; i++) {
        aun_red_buffer[i - 100] = aun_red_buffer[i];
        aun_ir_buffer[i - 100] = aun_ir_buffer[i];

        // update the signal min and max
        if (un_min > aun_red_buffer[i])
            un_min = aun_red_buffer[i];
        if (un_max < aun_red_buffer[i])
            un_max = aun_red_buffer[i];
    }
    // take 100 sets of samples before calculating the heart rate.
    for (i = 400; i < 500; i++) {
        un_prev_data = aun_red_buffer[i - 1];
        while (MAX30102_INT == 1)
            ;
        max30102_FIFO_ReadBytes(REG_FIFO_DATA, temp);
        aun_red_buffer[i] = (long)((long)((long)temp[0] & 0x03) << 16) | (long)temp[1] << 8 | (long)temp[2]; // Combine values to get the actual number
        aun_ir_buffer[i] = (long)((long)((long)temp[3] & 0x03) << 16) | (long)temp[4] << 8 | (long)temp[5];  // Combine values to get the actual number

        if (aun_red_buffer[i] > un_prev_data) {
            f_temp = aun_red_buffer[i] - un_prev_data;
            f_temp /= (un_max - un_min);
            f_temp *= MAX_BRIGHTNESS;
            n_brightness -= (int)f_temp;
            if (n_brightness < 0)
                n_brightness = 0;
        } else {
            f_temp = un_prev_data - aun_red_buffer[i];
            f_temp /= (un_max - un_min);
            f_temp *= MAX_BRIGHTNESS;
            n_brightness += (int)f_temp;
            if (n_brightness > MAX_BRIGHTNESS)
                n_brightness = MAX_BRIGHTNESS;
        }
        // send samples and calculation result to terminal program through UART
        if (ch_hr_valid == 1 && ch_spo2_valid == 1 && n_heart_rate < 120 && n_sp02 < 101) //**/ ch_hr_valid == 1 && ch_spo2_valid ==1 && n_heart_rate<120 && n_sp02<101
        {
            dis_hr = n_heart_rate;
            dis_spo2 = n_sp02;

        } else {
            dis_hr = 0;
            dis_spo2 = 0;
        }
    }
    maxim_heart_rate_and_oxygen_saturation(aun_ir_buffer, n_ir_buffer_length, aun_red_buffer, &n_sp02, &ch_spo2_valid, &n_heart_rate, &ch_hr_valid);
}
