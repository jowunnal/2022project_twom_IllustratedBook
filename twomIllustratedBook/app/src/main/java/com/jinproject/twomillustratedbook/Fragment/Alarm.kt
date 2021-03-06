package com.jinproject.twomillustratedbook.Fragment

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.jinproject.twomillustratedbook.Adapter.AlarmAdapter
import com.jinproject.twomillustratedbook.Adapter.AlarmSelectedAdapter
import com.jinproject.twomillustratedbook.Database.BookApplication
import com.jinproject.twomillustratedbook.Database.Entity.DropListMonster
import com.jinproject.twomillustratedbook.Database.Entity.Timer
import com.jinproject.twomillustratedbook.Item.*
import com.jinproject.twomillustratedbook.R
import com.jinproject.twomillustratedbook.Service.AlarmServerService
import com.jinproject.twomillustratedbook.Service.WService
import com.jinproject.twomillustratedbook.ViewModel.AlarmModel
import com.jinproject.twomillustratedbook.databinding.AlarmBinding
import com.jinproject.twomillustratedbook.databinding.AlarmUserSelectedItemBinding
import com.jinproject.twomillustratedbook.listener.OnBossNameClickedListener
import com.jinproject.twomillustratedbook.listener.OnItemClickListener
import kotlinx.coroutines.*
import java.util.*
import kotlin.collections.ArrayList

class Alarm : Fragment() {
    var _binding:AlarmBinding ?=null
    val binding get() = _binding!!
    val timeModel: AlarmModel by viewModels()
    val bossModel: BookViewModel by activityViewModels(){ BookViewModelFactory((activity?.application as BookApplication).repository) }
    val adapter:AlarmAdapter by lazy { AlarmAdapter() }
    val selectedAdapter by lazy{AlarmSelectedAdapter()}
    private var clickable=-1
    lateinit var timerSharedPref:SharedPreferences
    private val ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 404
    private var listToWservice=ArrayList<Timer>()
    private lateinit var monster:DropListMonster
    lateinit var alarmItem:AlarmItem
    lateinit var navController:NavController
    private var serverBossList=ArrayList<TimerItem>()
    private var mRewardedAd: RewardedAd? = null

    var day:Int=0
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {   // ??????????????? ????????? ??????
            if (!Settings.canDrawOverlays(requireActivity())) { // ????????? ?????? ????????? ??????
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+requireActivity().packageName))
                startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE)
            }
        }
        _binding=AlarmBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        navController=Navigation.findNavController(view)
        timerSharedPref=requireActivity().getSharedPreferences("TimerSharedPref", Context.MODE_PRIVATE)
        binding.alarmRecyclerView.layoutManager=LinearLayoutManager(requireActivity(),LinearLayoutManager.VERTICAL,false)
        binding.alarmRecyclerView.adapter=adapter
        val alarmDialog=Dialog(requireActivity())
        alarmDialog.setContentView(R.layout.alarm_currentlist_dialog)

        binding.alarmSelectedList.adapter=selectedAdapter
        binding.alarmSelectedList.layoutManager=GridLayoutManager(requireActivity(),2,GridLayoutManager.VERTICAL,false)
        val selectedBossList=requireActivity().getSharedPreferences("bossList",Context.MODE_PRIVATE)
        val boss=selectedBossList.getStringSet("boss", mutableSetOf("?????????"))
        val list=ArrayList<String>()
        list.addAll(boss!!)
        selectedAdapter.setItems(list)
        selectedAdapter.notifyDataSetChanged()

        val cal= Calendar.getInstance()

        selectedAdapter.setClickListener(object : OnBossNameClickedListener{
            override fun setOnItemClickListener(
                v: View,
                pos: Int,
                binding: AlarmUserSelectedItemBinding
            ) {
                when (clickable) {
                    -1 -> {
                        clickable=pos
                        CoroutineScope(Dispatchers.IO).launch {
                            monster=bossModel.getMonsInfo(selectedAdapter.getItem(pos))
                            alarmItem=AlarmItem(monster.mons_name,monster.mons_imgName,monster.mons_Id,monster.mons_gtime)
                        }
                    }
                    pos -> {
                        clickable=-1
                    }
                    else -> {
                        Toast.makeText(requireActivity(),"????????? 2???????????? ???????????? ????????????. ????????? ????????????????????????", Toast.LENGTH_SHORT).show()
                    }
                }
                binding.alarmUserSelectedItemSwitch.setOnCheckedChangeListener { button, b ->
                    when(b){
                        true->getSettingDrawOverlays(1,binding.alarmUserSelectedItem.text.toString())
                        false->getSettingDrawOverlays(0,binding.alarmUserSelectedItem.text.toString())
                    }
                }

            }
        })

        binding.timerInput.setOnClickListener { // dialog ????????????
            val picker=MyTimePicker()
            picker.setOnTimeListener(){hour, min, sec ->  
                cal.set(Calendar.HOUR_OF_DAY,hour)
                cal.set(Calendar.MINUTE,min)
                cal.set(Calendar.SECOND,sec)
            }
            picker.show(requireActivity().supportFragmentManager,"timePicker")
        }
        var adRequest = AdRequest.Builder().build()

        RewardedAd.load(requireActivity(),"ca-app-pub-3630394418001517/7487969905", adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                mRewardedAd = null
            }

            override fun onAdLoaded(rewardedAd: RewardedAd) {
                mRewardedAd = rewardedAd
            }
        })
        mRewardedAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
            override fun onAdClicked() {
                // Called when a click is recorded for an ad.
            }

            override fun onAdDismissedFullScreenContent() {
                // Called when ad is dismissed.
                // Set the ad reference to null so you don't show the ad a second time.
                mRewardedAd=null
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                // Called when ad fails to show.
                mRewardedAd=null
            }

            override fun onAdImpression() {
                // Called when an impression is recorded for an ad.
            }

            override fun onAdShowedFullScreenContent() {
                // Called when ad is shown.
            }
        }
        binding.timerStart.setOnClickListener { // dialog????????? ???,?????? ??? ??????????????????????????? ??????????????? ?????????????????????, ????????????????????? ???????????????????????????
            mRewardedAd?.show(requireActivity(), OnUserEarnedRewardListener() {
                fun onUserEarnedReward(rewardItem: RewardItem) {
                    var rewardAmount = rewardItem.amount
                    var rewardType = rewardItem.type
                }
            })
            try {
                day = cal.get(Calendar.DAY_OF_WEEK)
                var hour = cal.get(Calendar.HOUR_OF_DAY) + ((monster.mons_gtime / 60) / 60)
                var min = cal.get(Calendar.MINUTE) + ((monster.mons_gtime / 60) % 60)
                var sec = cal.get(Calendar.SECOND) + ((monster.mons_gtime % 60))
                while (sec >= 60) {
                    min += 1
                    sec -= 60
                }
                while (min >= 60) {
                    hour += 1
                    min -= 60
                }
                while (hour >= 24) {
                    hour -= 24
                    day += 1
                }
                timeModel.setAlarm(
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    alarmItem
                )
                bossModel.setTimer(day, hour, min, sec, monster.mons_name, 1)
            }catch (e:kotlin.UninitializedPropertyAccessException){
                Toast.makeText(requireActivity(),"?????? ????????? ??????????????????!",Toast.LENGTH_SHORT).show()
            }
        }

        // ???????????? ??????????????? ?????????????????? ????????????????????? observer ??? ???????????? ?????????????????? ?????? ?????????
        bossModel.timer.observe(viewLifecycleOwner, Observer {
            adapter.setItems(it)
            adapter.notifyDataSetChanged()
            serverBossList.clear()
            for(data in it){
                serverBossList.add(TimerItem(data.timer_name,data.timer_day,data.timer_hour,data.timer_min,data.timer_sec))
            }

            listToWservice.clear()
            for(item in it){
                if(item.timer_ota==1){
                    listToWservice.add(item)
                }
            }
            Collections.sort(listToWservice, object :Comparator<Timer>{ // ???-???-???-??? ????????? ??????
                override fun compare(p0: Timer?, p1: Timer?): Int {
                    if(p0!!.timer_day.compareTo(p1!!.timer_day)==0){
                        if(p0.timer_hour.compareTo(p1.timer_hour)==0){
                            if(p0.timer_min.compareTo(p1.timer_min)==0){
                                if(p0.timer_sec.compareTo(p1.timer_sec)==0){
                                    return p0.timer_name.compareTo(p1.timer_name)
                                }
                                return p0.timer_sec.compareTo(p1.timer_sec)
                            }
                            return p0.timer_min.compareTo(p1.timer_min)
                        }
                        return p0.timer_hour.compareTo(p1.timer_hour)
                    }
                    return p0.timer_day.compareTo(p1.timer_day)
                }
            })
            if(listToWservice.isNotEmpty()){ //??????????????? ????????? ????????????????????? ??????????????? ???????????????
                requireActivity().startService(Intent(activity, WService::class.java).apply { putExtra("list",listToWservice) })
            }
            else{ // ???????????????(??????????????????????????????) ??????????????? ?????? ??????????????? ???
                requireActivity().startService(Intent(activity, WService::class.java))
            }
        })

        adapter.setItemClickListener(object:OnItemClickListener{ // ?????????????????? ?????? ????????? ???????????? ?????????
            override fun OnHomeItemClick(v: View, pos: Int) {
                val item=adapter.getItem(pos)
                alarmDialog.findViewById<Button>(R.id.alarm_cancel).setOnClickListener{
                    alarmDialog.cancel()
                }
                alarmDialog.findViewById<Button>(R.id.alarm_delete).setOnClickListener {
                    bossModel.setTimer(0,0,0,0,item.name,0)
                    var code=0

                    CoroutineScope(Dispatchers.Main).launch {
                        withContext(Dispatchers.IO){code=bossModel.getMonsInfo(item.name).mons_Id}
                        timeModel.clearAlarm(code)
                        timeModel.clearAlarm(code+300)
                        alarmDialog.cancel()
                    }

                }
                alarmDialog.setCanceledOnTouchOutside(true)
                alarmDialog.setCancelable(true)
                alarmDialog.show()
            }
        })

        setHasOptionsMenu(true)
        val loginPreference=requireActivity().getSharedPreferences("login",Context.MODE_PRIVATE)
        binding.serverOutput.setOnClickListener {

            val db:DatabaseReference=FirebaseDatabase.getInstance().reference
            try {
                val id=loginPreference.getString("id","")!!
                val pw=loginPreference.getString("pw","")!!
                val key=loginPreference.getString("key","")!!
                val authorityCode=loginPreference.getString("authorityCode","")
                db.child("RoomList").child(key).get().addOnSuccessListener {
                    if(it.child("authorityCode").value==authorityCode){
                        db.child("RoomList").child(key).setValue(Room(id,pw,serverBossList,authorityCode))
                        Toast.makeText(requireActivity(),"????????? ????????? ?????????????????????.",Toast.LENGTH_SHORT).show()
                    }else{
                        Toast.makeText(requireActivity(),"?????? ????????? ????????????.",Toast.LENGTH_SHORT).show()
                    }
                }
            }catch (e:kotlin.UninitializedPropertyAccessException){
                Toast.makeText(requireActivity(),"?????? ????????? ??????????????????!",Toast.LENGTH_SHORT).show()
            }

        }
        binding.serverOnoff.setOnClickListener {
            when(loginPreference.getBoolean("statue",false)){
                false->{
                    requireActivity().startService(Intent(activity,AlarmServerService::class.java))
                    loginPreference.edit().putBoolean("statue",true).apply()
                    binding.serverStatueTv.text="????????????: "+when(loginPreference.getBoolean("statue",false)){false->"????????????" true->"?????????"}+
                            "/?????????ID: "+loginPreference.getString("id","")
                    Toast.makeText(requireActivity(),"??????????????? ???????????? ?????? ???????????????.",Toast.LENGTH_SHORT).show()
                }
                true->{
                    requireActivity().stopService(Intent(activity,AlarmServerService::class.java))
                    loginPreference.edit().putBoolean("statue",false).apply()
                    binding.serverStatueTv.text="????????????: "+when(loginPreference.getBoolean("statue",false)){false->"????????????" true->"?????????"}+
                            "/?????????ID: "+loginPreference.getString("id","")
                    Toast.makeText(requireActivity(),"??????????????? ???????????? ?????? ????????????.",Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.serverStatueTv.text="????????????: "+when(loginPreference.getBoolean("statue",false)){false->"????????????" true->"?????????"}+
                "/?????????ID: "+loginPreference.getString("id","")
    }


    fun getSettingDrawOverlays(ota:Int,mons_name:String){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {   // ??????????????? ????????? ??????
            if (!Settings.canDrawOverlays(requireActivity())) {              // ????????? ?????? ????????? ??????
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+requireActivity().packageName))
                startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE)
            }
            else{
                bossModel.setOta(ota,mons_name)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        requireActivity().menuInflater.inflate(R.menu.timer_option_menu,menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.icon_fix->navController.navigate(R.id.action_alarm_to_timer)

            R.id.icon_addTime->if(!timerSharedPref.getBoolean("flag",false)){
                timerSharedPref.edit().putBoolean("flag",true).apply()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {   // ??????????????? ????????? ??????
                    if (!Settings.canDrawOverlays(requireActivity())) { // ????????? ?????? ????????? ??????
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+requireActivity().packageName))
                        startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE)
                    } else {
                        if(listToWservice.isNotEmpty()){
                            requireActivity().startService(Intent(activity, WService::class.java).apply { putExtra("list",listToWservice) })
                        }
                        else{
                            requireActivity().startService(Intent(activity, WService::class.java))
                        }
                    }
                }
            }
            else{
                timerSharedPref.edit().putBoolean("flag",false).apply()
                requireActivity().stopService(Intent(activity, WService::class.java))
            }
            R.id.icon_login->navController.navigate(R.id.action_alarm_to_login)

        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroyView() {
        _binding=null
        super.onDestroyView()
    }
}