#include "git.h"

Data_TypeDef Data_init;						  // �豸���ݽṹ��
Threshold_Value_TypeDef threshold_value_init; // �豸��ֵ���ýṹ��
Device_Satte_Typedef device_state_init;		  // �豸״̬

extern int32_t n_sp02;		 // SPO2 value
extern int8_t ch_spo2_valid; // indicator to show if the SP02 calculation is valid
extern int32_t n_heart_rate; // heart rate value
extern int8_t ch_hr_valid;	 // indicator to show if the heart rate calculation is valid

void errorLog(U8 num)
{
	while (1)
	{
		printf("ERROR%d\r\n", num);
	}
}
// ��ȡGPS��λ��Ϣ
void parseGpsBuffer()
{
	char *subString;
	char *subStringNext;
	char i = 0;
	char usefullBuffer[2];
	if (Save_Data.isGetData)
	{
		Save_Data.isGetData = false;
		for (i = 0; i <= 6; i++)
		{
			if (i == 0)
			{
				if ((subString = strstr(Save_Data.GPS_Buffer, ",")) == NULL)
					errorLog(1); // ��������
			}
			else
			{
				subString++;
				if ((subStringNext = strstr(subString, ",")) != NULL)
				{
					switch (i)
					{
					case 1:
						memcpy(Save_Data.UTCTime, subString, subStringNext - subString);
						break; // ��ȡUTCʱ��
					case 2:
						memcpy(usefullBuffer, subString, subStringNext - subString);
						break; // ��ȡUTCʱ��
					case 3:
						memcpy(Save_Data.latitude, subString, subStringNext - subString);

						break; // ��ȡγ����Ϣ
					case 4:
						memcpy(Save_Data.N_S, subString, subStringNext - subString);
						break; // ��ȡN/S
					case 5:
						memcpy(Save_Data.longitude, subString, subStringNext - subString);
						break; // ��ȡ������Ϣ
					case 6:
						memcpy(Save_Data.E_W, subString, subStringNext - subString);
						break; // ��ȡE/W

					default:
						break;
					}

					subString = subStringNext;
					Save_Data.isParseData = true;
					if (usefullBuffer[0] == 'A')
					{
						device_state_init.location_state = 1;
						Save_Data.isUsefull = true;
					}

					else if (usefullBuffer[0] == 'V')
						Save_Data.isUsefull = false;

					// if (Save_Data.latitude > 0 && Save_Data.longitude > 0)
					// {
					// }
				}
				else
				{
					errorLog(2); // ��������
				}
			}
		}
	}
}
F32 longitude_sum, latitude_sum;
U8 longitude_int, latitude_int;
void printGpsBuffer()
{
	// ת��Ϊ����
	longitude_sum = atof(Save_Data.longitude);
	latitude_sum = atof(Save_Data.latitude);
	// printf("ά�� = %.5f %.5f\r\n",longitude_sum,latitude_sum);
	// ����
	longitude_int = longitude_sum / 100;
	latitude_int = latitude_sum / 100;

	// ת��Ϊ��γ��
	longitude_sum = longitude_int + ((longitude_sum / 100 - longitude_int) * 100) / 60;
	latitude_sum = latitude_int + ((latitude_sum / 100 - latitude_int) * 100) / 60;
	device_state_init.location_state = 1;
	if (Save_Data.isParseData)
	{
		Save_Data.isParseData = false;

		// printf("Save_Data.UTCTime = %s\r\n", Save_Data.UTCTime);
		if (Save_Data.isUsefull)
		{
			Save_Data.isUsefull = false;
		}
		else
		{
			// printf("GPS DATA is not usefull!\r\n");
		}
	}
}
// ��ȡ���ݲ���
mySta Read_Data(Data_TypeDef *Device_Data)
{
	device_state_init.Distance = (Get_SR04_Distance2() * 331) * 1.0 / 1000;
	obtain_bus(); // ����
	// ��ȡ��γ��
	parseGpsBuffer();
	if (device_state_init.location_state == 1)
	{
		// ������γ��
		printGpsBuffer();
	} else {
		longitude_sum = 104.065850;
		latitude_sum = 30.657361;
	}
	
	return MY_SUCCESSFUL;
}
// ��ʼ��
mySta Reset_Threshole_Value(Threshold_Value_TypeDef *Value, Device_Satte_Typedef *device_state)
{

// longitude_sum = 111.434380;
// latitude_sum = 27.197769;
//  // д
//  W_Test();
	// ��
	R_Test();
	// ״̬����
	device_state->check_device = 0;

	return MY_SUCCESSFUL;
}
// ����OLED��ʾ��������
mySta Update_oled_massage()
{
#if OLED // �Ƿ��
	char str[50];
	sprintf(str, "--- MAX30102 --");
	if (0 < n_heart_rate && n_heart_rate < 150 && ch_spo2_valid)
	{
		sprintf(str, "Heart: %03d    ", n_heart_rate);
		OLED_ShowCH(0, 0, (unsigned char *)str);
		sprintf(str, "SpO2 : %03d    ", n_sp02);
		OLED_ShowCH(0, 2, (unsigned char *)str);
	}
	else
	{
		sprintf(str, "Heart: %03d    ", 0);
		OLED_ShowCH(0, 0, (unsigned char *)str);
		sprintf(str, "SpO2 : %03d    ", 0);
		OLED_ShowCH(0, 2, (unsigned char *)str);
	}
	if (device_state_init.Fall_State == 0)
	{
		sprintf(str, "����״̬ : ����");
	}
	else
	{
		sprintf(str, "����״̬ : ����");
	}
	OLED_ShowCH(0, 4, (unsigned char *)str);
	sprintf(str, "���� : %05d mm", device_state_init.Distance);
	// sprintf(str, "FallState : %d", device_state_init.Fall_State);
	OLED_ShowCH(0, 6, (unsigned char *)str);
	// sprintf(str, "����: %d-%d-%d   ", time.year, time.month, time.day);

#endif

	return MY_SUCCESSFUL;
}

// �����豸״̬
mySta Update_device_massage()
{
	// �Զ�ģʽ

	if (device_state_init.Key_State == 1 || device_state_init.Fall_State == 1||device_state_init.Distance<500)
	{
		BEEP = ~BEEP;
	}
	else
	{
		BEEP = 0;
	}
	if (Data_init.App)
	{
		switch (Data_init.App)
		{
		case 1:
			OneNet_SendMqtt(1); // �������ݵ�APP
			break;
		case 2:
			OneNet_SendMqtt(2); // �������ݵ�APP
			break;
		case 3:
			// OneNet_SendMqtt(3); // �������ݵ�APP
			break;
		case 4:
			// OneNet_SendMqtt(4); // �������ݵ�APP
			break;
		}
		Data_init.App = 0;
	}

	return MY_SUCCESSFUL;
}

// ��ʱ��
void Automation_Close(void)
{
	// ʵ��30s�Զ��л�����
}
// ��ⰴ���Ƿ���
static U8 num_on = 0;
static U8 key_old = 0;
void Check_Key_ON_OFF()
{
	U8 key;
	key = KEY_Scan(1);
	// ����һ�εļ�ֵ�Ƚ� �������ȣ������м�ֵ�ı仯����ʼ��ʱ
	if (key != 0 && num_on == 0)
	{
		key_old = key;
		num_on = 1;
	}
	if (key != 0 && num_on >= 1 && num_on <= Key_Scan_Time) // 25*10ms
	{
		num_on++; // ʱ���¼��
	}
	if (key == 0 && num_on > 0 && num_on < Key_Scan_Time) // �̰�
	{
		switch (key_old)
		{
		case KEY1_PRES:
			printf("Key1_Short\n");
			if (device_state_init.Fall_State == 1)
			{
				device_state_init.Fall_State = 0;
			}
			break;
		case KEY2_PRES:
			printf("Key2_Short\n");

			break;
		case KEY3_PRES:
			printf("Key3_Short\n");

			break;

		default:
			break;
		}
		num_on = 0;
	}
	else if (key == 0 && num_on >= Key_Scan_Time) // ����
	{
		switch (key_old)
		{
		case KEY1_PRES:
			printf("Key1_Long\n");
			if (device_state_init.Key_State == 1)
			{
				device_state_init.Key_State = 0;
			}
			else
			{
				device_state_init.Key_State = 1;
			}
			Data_init.App = 1;

			break;
		case KEY2_PRES:
			printf("Key2_Long\n");

			break;
		case KEY3_PRES:
			printf("Key3_Long\n");

			break;
		default:
			break;
		}
		num_on = 0;
	}
}
// ����json����
mySta massage_parse_json(char *message)
{

	cJSON *cjson_test = NULL; // ���json��ʽ
	cJSON *cjson_data = NULL; // ����
	const char *massage;
	// ������������
	u8 cjson_cmd; // ָ��,����

	/* ��������JSO���� */
	cjson_test = cJSON_Parse(message);
	if (cjson_test == NULL)
	{
		// ����ʧ��
		printf("parse fail.\n");
		return MY_FAIL;
	}

	/* ���θ���������ȡJSON���ݣ���ֵ�ԣ� */
	cjson_cmd = cJSON_GetObjectItem(cjson_test, "cmd")->valueint;
	/* ����Ƕ��json���� */
	cjson_data = cJSON_GetObjectItem(cjson_test, "data");

	switch (cjson_cmd)
	{
	case 0x01: // ��Ϣ��

		Data_init.Device_State = cJSON_GetObjectItem(cjson_data, "flage")->valueint;

		Data_init.App = 1;
		break;
	case 0x02: // ��Ϣ��

		break;
	case 0x03: // ���ݰ�

		break;
	case 0x04: // ���ݰ�
		Data_init.App = cjson_cmd + 1;

		break;
	default:
		break;
	}

	/* ���JSON����(��������)���������� */
	cJSON_Delete(cjson_test);

	return MY_SUCCESSFUL;
}
