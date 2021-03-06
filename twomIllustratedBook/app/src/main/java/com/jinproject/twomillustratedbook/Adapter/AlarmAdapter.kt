package com.jinproject.twomillustratedbook.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jinproject.twomillustratedbook.Database.Entity.Timer
import com.jinproject.twomillustratedbook.Item.AlarmItem
import com.jinproject.twomillustratedbook.Item.TimerItem
import com.jinproject.twomillustratedbook.databinding.AlarmItemBinding
import com.jinproject.twomillustratedbook.listener.OnItemClickListener
import java.util.*
import kotlin.collections.ArrayList

class AlarmAdapter :RecyclerView.Adapter<AlarmAdapter.ViewHolder>() ,OnItemClickListener{
    private var items=ArrayList<TimerItem>()
    private var mlistener:OnItemClickListener ?= null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(AlarmItemBinding.inflate(LayoutInflater.from(parent.context)) )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun getItem(pos:Int):TimerItem{
        return items[pos]
    }
    fun setItems(items:List<Timer>){
        this.items.clear()
        for(item in items){
            this.items.add(TimerItem(item.timer_name,item.timer_day,item.timer_hour,item.timer_min,item.timer_sec))
        }
        Collections.sort(this.items, object :Comparator<TimerItem>{
            override fun compare(p0: TimerItem?, p1: TimerItem?): Int {
                if(p0!!.day.compareTo(p1!!.day)==0){
                    if(p0.hour.compareTo(p1.hour)==0){
                        if(p0.min.compareTo(p1.min)==0){
                            if(p0.sec.compareTo(p1.sec)==0){
                                return p0.name.compareTo(p1.name)
                            }
                            return p0.sec.compareTo(p1.sec)
                        }
                        return p0.min.compareTo(p1.min)
                    }
                    return p0.hour.compareTo(p1.hour)
                }
                return p0.day.compareTo(p1.day)
            }
        })
    }

    override fun OnHomeItemClick(v: View, pos: Int) {
        mlistener?.OnHomeItemClick(v,pos)
    }

    fun setItemClickListener(listener: OnItemClickListener){
        this.mlistener=listener
    }

    inner class ViewHolder(private val binding:AlarmItemBinding) :RecyclerView.ViewHolder(binding.root){
        fun bind(item:TimerItem){
            var myday=""
            binding.alarmName.text=item.name
            when(item.day){
                1->myday="???"
                2->myday="???"
                3->myday="???"
                4->myday="???"
                5->myday="???"
                6->myday="???"
                7->myday="???"
            }
            binding.alarmDay.text="("+myday+")"
            binding.alarmHour.text=item.hour.toString()+"???"
            binding.alarmMin.text=item.min.toString()+"???"
            binding.alarmSec.text=item.sec.toString()+"???"
            binding.root.setOnClickListener {
                OnHomeItemClick(it,adapterPosition)
            }
        }
    }
}