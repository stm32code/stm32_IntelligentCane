package com.example.smartcutchgps

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.common.BaiduMapSDKException
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.utils.SpatialRelationUtil
import com.blankj.utilcode.util.LogUtils
import com.example.smartcutchgps.bean.Receive
import com.example.smartcutchgps.chart.CreationChart
import com.example.smartcutchgps.dao.Heart
import com.example.smartcutchgps.dao.HeartDao
import com.example.smartcutchgps.databinding.ActivityMainBinding
import com.example.smartcutchgps.utils.BeatingAnimation
import com.example.smartcutchgps.utils.Common
import com.example.smartcutchgps.utils.LimitedArrayList
import com.example.smartcutchgps.utils.MToast
import com.example.smartcutchgps.utils.MapUtil
import com.example.smartcutchgps.utils.TimeCycle
import com.google.gson.Gson
import com.gyf.immersionbar.ImmersionBar
import com.itfitness.mqttlibrary.MQTTHelper
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity(), BaiduMap.OnMapStatusChangeListener,
    BaiduMap.OnMarkerClickListener {
    private lateinit var binding: ActivityMainBinding
    private var isDebugView = false //是否显示debug界面
    private var arrayList = ArrayList<String>()// debug消息列表
    private var adapter: ArrayAdapter<String>? = null// debug适配器
    private var onlineFlag = false //是否在线标识
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private var heartGather = LimitedArrayList<Int>(60) //心率频率集合，最大记录60个
    private lateinit var dao: HeartDao
    private var loginTimer: CountDownTimer? = null//协成定时器
    private lateinit var myMap: BaiduMap
    private var isFirstMap = true //是否第一次定位
    private var setLat: Double = 0.0
    private var setLong: Double = 0.0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        sharedPreferences = getSharedPreferences("Location", MODE_PRIVATE)
//        editor = sharedPreferences.edit()
        getPermission()
        dao = HeartDao(this)
        mqttServer()
        initView()
        isOnline()
    }

    /***
     * 初始化地图
     */
    private fun initMap() {
        myMap = binding.map.map
        myMap.isMyLocationEnabled = true // 开启定位图层
    }

    /**
     * 界面的初始化
     */
    private fun initView() {
        setSupportActionBar(binding.toolbar)
        ImmersionBar.with(this).init()
        debugView()
        //初始化图表视图
        CreationChart.initChart(binding.heartChart)
        warringLayout(false, "")
        initMap() //初始化地图
        // 跳转至高德地图
        binding.toGaode.setOnClickListener {
            // 构建带地名搜索关键字的 Uri
//            val uri = "amapuri://keywordNavi?sourceApplication=appname&keyword=$searchKeyword"

            var uri = "amapuri://"
            // 目标经纬度
            if (setLat != 0.0 && setLong != 0.0) {
                // 构建带经纬度的 Uri
                uri = "amapuri://route/plan/?dlat=$setLat&dlon=$setLong&dev=0&t=0"
            }


            val intent = Intent(Intent.ACTION_VIEW)
            intent.setPackage(MapUtil.PN_GAODE_MAP)
//            intent.data = Uri.parse("amapuri://") //直接跳转
            intent.data = Uri.parse(uri)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("跳转高德地图错误", e.toString())
                MToast.mToast(this, "未安装")
            }
        }
    }

    /**
     * @brief debug界面的初始化
     */
    private fun debugView() {
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayList)
        binding.debugView.adapter = adapter
    }

    /**
     * mqtt服务
     */
    private fun mqttServer() {
        Common.mqttHelper = MQTTHelper(
            this, Common.Sever, Common.DriveID, Common.DriveName, Common.DrivePassword, true, 30, 60
        )
        Common.mqttHelper!!.connect(Common.ReceiveTopic, 1, true, object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                MToast.mToast(this@MainActivity, "连接已经断开")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                //收到消息
                val data = Gson().fromJson(message.toString(), Receive::class.java)
                LogUtils.eTag("接收到消息", message?.payload?.let { String(it) })
                onlineFlag = true
                binding.online.text = "在线"
                debugViewData(2, message?.payload?.let { String(it) }!!)
                println(data)
                if (data != null) {
                    analysisOfData(data)
                } else {
                    MToast.mToast(this@MainActivity, "数据异常")
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {

            }
        })

    }

    /***
     * 设置地图根据经纬度定位
     *  lati：经度
     *  longi：维度
     */
    private fun setMap(
        lati: Double,
        longi: Double,
    ) {
        myMap.setMyLocationData(
            MyLocationData.Builder().accuracy(200F) // 设置方向信息，顺时针0-360
                .direction(-1F).latitude(lati).longitude(longi).build()
        )
        Log.d("经纬度", "纬度:${lati},经度:${longi}")
        // 跳转到定位的位置
        if (isFirstMap) {
            isFirstMap = false
            val ll = LatLng(lati, longi)
            val builder = MapStatus.Builder()
            builder.target(ll).zoom(19f)
            myMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()))
        }
    }

    /**
     * @brief 动态获取权限
     */
    private fun getPermission() {
        val perms = mutableListOf<String>()
        perms.add(Manifest.permission.ACCESS_WIFI_STATE)
        perms.add(Manifest.permission.CHANGE_WIFI_STATE)
        perms.add(Manifest.permission.INTERNET)
        perms.add(Manifest.permission.ACCESS_NETWORK_STATE)
        perms.add(Manifest.permission.READ_PHONE_STATE)
        perms.add(Manifest.permission.WAKE_LOCK)
        perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.USE_EXACT_ALARM)
        }
        if (!EasyPermissions.hasPermissions(this, *perms.toTypedArray())) {
            EasyPermissions.requestPermissions(
                this, "这是必要的权限", 100, *perms.toTypedArray()
            )
        }
    }

    /***
     * 数据处理
     */
    private fun analysisOfData(data: Receive) {
        try {
            var isWaring = false
            var theWaring = false
            if (data.heart != null) {
                CreationChart.addEntry(
                    binding.heartChart, data.heart!!.toFloat()
                )
                heartGather.add(data.heart!!.toInt())
                isWaring = data.heart!!.toInt() > 120
            }
            if (data.waring != null) {
                if (data.waring == "1") {
                    theWaring = true
                    warringLayout(true, "老人摔倒")
                } else {
                    theWaring = false
                    warringLayout(false, "老人摔倒")
                }

            }
            if (data.key_waring != null) {
                if (!theWaring) if (data.key_waring == "1") {
                    warringLayout(true, "老人触发手动报警")
                } else {
                    warringLayout(false, "老人触发手动报警")
                }
            }
            if (data.disc != null) {
                binding.barrierText.text = data.disc
            }
            insetDataToDB(data.heart, isWaring)
            binding.heartText.text = //平均心率显示
                if (heartGather.size > 0) String.format(
                    "%d", heartGather.sum() / heartGather.size
                ) else "0"
            if (data.lat != null && data.log != null) {
                val lat = data.lat?.toDouble()
                val log = data.log?.toDouble()
                if (lat != null && log != null) {
                    setMap(lat, log)
                    setLat = lat
                    setLong = log
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MToast.mToast(this, "数据解析失败:$e")
        }
    }

    /**
     * 向数据库插入数据
     * @param data 心率数据
     * @param isWaring 是否异常数据
     */
    private fun insetDataToDB(data: String?, isWaring: Boolean) {
        if (data != null) {
            dao.insert(Heart(heart = data.toInt(), isWaring = if (isWaring) 1 else 0))
        }
    }


    /***
     * 判断硬件是否在线
     */
    private fun isOnline() {
        loginTimer = object : CountDownTimer(15000, 5000) {
            //18000ms运行一次onTick里面的方法
            override fun onTick(p0: Long) {
                Log.e("定时器测试:", "onlineFlag:${onlineFlag}")
                runOnUiThread {
                    binding.online.text = if (onlineFlag) "在线" else "离线"
                }
                onlineFlag = false

            }

            override fun onFinish() {
                isOnline()
            }
        }.start()
    }

    /**
     * @brief debug界面数据添加
     * @param str 如果为 1 添加发送数据到界面   为 2 添加接受消息到界面
     */
    private fun debugViewData(str: Int, data: String) {
        if (arrayList.size >= 255) arrayList.clear()
        runOnUiThread {
            when (str) {
                1 -> { //发送的消息
                    arrayList.add("目标主题:${Common.ReceiveTopic} \n时间:${TimeCycle.getDateTime()}\n发送消息:$data ")
                }

                2 -> {
                    arrayList.add("来自主题:${Common.ReceiveTopic} \n时间:${TimeCycle.getDateTime()}\n接到消息:$data ")
                }
            }
            // 在添加新数据之后调用以下方法，滚动到列表底部
            binding.debugView.post {
                binding.debugView.setSelection(adapter?.count?.minus(1)!!)
            }
            adapter?.notifyDataSetChanged()
        }

    }


    /**
     * @brief 显示警告弹窗和设置弹窗内容
     * @param visibility 是否显示
     * @param str 显示内容
     */
    private fun warringLayout(visibility: Boolean, str: String) {
        if (visibility) {
            binding.warringLayout.visibility = View.VISIBLE
            binding.warringText.text = str
            BeatingAnimation().onAnimation(binding.warringImage)
        } else {
            binding.warringLayout.visibility = View.GONE
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        loginTimer?.cancel()
//在activity执行onDestroy时执行binding.map.onDestroy()，销毁地图
        binding.map.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        //在activity执行onResume时执行binding.map.onResume ()，重新绘制加载地图
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        //在activity执行onPause时执行binding.map.onPause ()，暂停地图的绘制
        binding.map.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        //在activity执行onSaveInstanceState时执行binding.map.onSaveInstanceState (outState)，保存地图当前的状态
        binding.map.onSaveInstanceState(outState)
    }

    /**
     * 手势操作地图，设置地图状态等操作导致地图状态开始改变。
     *
     * @param p0 地图状态改变开始时的地图状态
     */
    override fun onMapStatusChangeStart(p0: MapStatus?) {
        Log.d("地图状态", "地图状态改变开始")
    }

    /**
     * 手势操作地图，设置地图状态等操作导致地图状态开始改变。
     *
     * @param p0 地图状态改变开始时的地图状态
     *
     * @param p1 地图状态改变的原因
     */
//用户手势触发导致的地图状态改变,比如双击、拖拽、滑动底图
//int REASON_GESTURE = 1;
//SDK导致的地图状态改变, 比如点击缩放控件、指南针图标
//int REASON_API_ANIMATION = 2;
//开发者调用,导致的地图状态改变
//int REASON_DEVELOPER_ANIMATION = 3;
    override fun onMapStatusChangeStart(p0: MapStatus?, p1: Int) {
        Log.d("地图状态", "地图状态改变的原因：${p1}")

    }

    /**
     * 地图状态变化中
     *
     * @param p0 当前地图状态
     */
    override fun onMapStatusChange(p0: MapStatus?) {
        Log.d("地图状态", "地图状态变化中，当前地图分级:${myMap.mapStatus.zoom}")
    }

    /**
     * 地图状态改变结束
     *
     * @param p0 地图状态改变结束后的地图状态
     */
    override fun onMapStatusChangeFinish(p0: MapStatus?) {
        Log.d("地图状态", "地图状态改变结束")
    }

    //权限申请回调
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //将申请权限结果的回调交由easypermissions处理
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_scrolling, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.setDebugView -> { // set debug view is enabled or disabled
                isDebugView = !isDebugView
                binding.debugView.visibility = if (isDebugView) View.VISIBLE else View.GONE
                true
            }

            R.id.setHistoryView -> {
                startActivity(Intent(this@MainActivity, RecordActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onMarkerClick(p0: Marker?): Boolean {
        TODO("Not yet implemented")
    }

}