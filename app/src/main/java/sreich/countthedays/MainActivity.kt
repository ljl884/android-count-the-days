package sreich.countthedays

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.view.GestureDetectorCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.text.format.DateUtils
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import com.fatboyindustrial.gsonjodatime.Converters
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.joda.time.DateTime
import java.util.*

class MainActivity : AppCompatActivity() {
    var counterList = mutableListOf<DayCounter>() //loadSave()

    var editingIndex = -1

    val gson = Converters.registerDateTime(GsonBuilder()).create()!!
    lateinit var adapter: DayCounterAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        val createNewFab = findViewById(R.id.fab) as FloatingActionButton
        createNewFab.setOnClickListener(FabCreateNewClickListener())

        counterList = loadSave()

        val listView = findViewById(R.id.listview) as RecyclerView
        registerForContextMenu(listView)

        listView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))

        adapter = DayCounterAdapter(context = this, counterList = counterList)
        listView.adapter = adapter
        listView.layoutManager = LinearLayoutManager(applicationContext)
        listView.itemAnimator = DefaultItemAnimator()

        listView.addOnItemTouchListener(RecyclerTouchListener(applicationContext, listView, ListClickListener()))
    }

    /**
     * data for testing and first time startup
     */
    private fun sampleData(): MutableList<DayCounter> {
        val now = DateTime.now()

        val list = mutableListOf(
                Pair("Since I brushed my teeth",
                     now.minusYears(0).minusMonths(0).minusDays(1).minusHours(0).minusMinutes(0)),
                Pair("Since I gave cat her pills",
                     now.minusYears(0).minusMonths(0).minusDays(24).minusHours(0).minusMinutes(0)),
                Pair("New phone arrives",
                     now.minusYears(0).minusMonths(0).plusDays(24).minusHours(0).minusMinutes(0)),
                Pair("Water filter changed",
                     now.minusYears(0).minusMonths(3).minusDays(0).minusHours(0).minusMinutes(0)),
                Pair("Car bought",
                     now.minusYears(1).minusMonths(3).minusDays(0).minusHours(0).minusMinutes(0)),
                Pair("New TV bought",
                     now.minusYears(0).minusMonths(3).minusDays(24).minusHours(0).minusMinutes(0)),
                Pair("Sofa arrived",
                     now.minusYears(1).minusMonths(3).minusDays(10).minusHours(0).minusMinutes(0)),
                Pair("Renewed subscription",
                     now.minusYears(1).minusMonths(3).minusDays(10).minusHours(10).minusMinutes(52))
                                )

        val newCounterList = mutableListOf<DayCounter>()
        list.forEach { pair ->
            newCounterList.add(DayCounter(name = pair.first, dateTime = pair.second))
        }

        return newCounterList
    }

    enum class ActivityRequest(val value: Int) {
        EditListItem(0),
        CreateListItem(1)
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)

        if (v!!.id == R.id.listview) {
            menuInflater.inflate(R.menu.menu_list, menu)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.clear_all -> {
                val builder = AlertDialog.Builder(this)

                builder.setTitle("Confirm")
                builder.setMessage("Are you sure?")

                builder.setPositiveButton("YES", object : DialogInterface.OnClickListener {

                    override fun onClick(dialog: DialogInterface, which: Int) {

                        counterList.clear()

                        adapter.notifyDataSetChanged()

                        dialog.dismiss()
                    }
                })

                builder.setNegativeButton("NO", object : DialogInterface.OnClickListener {

                    override fun onClick(dialog: DialogInterface, which: Int) {
                        dialog.dismiss()
                    }
                })

                val alert = builder.create()
                alert.show()

                return true
            }

            else -> return super.onContextItemSelected(item)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val position = selectedItem
        when (item.itemId) {
            R.id.delete -> {
                //delete this one
                counterList.removeAt(position)

                adapter.notifyDataSetChanged()
                return true
            }

            R.id.reset -> {
                val counter = counterList[position]
                counter.dateTime = DateTime.now()

                adapter.notifyDataSetChanged()
                return true
            }

            else -> return super.onContextItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ActivityRequest.EditListItem.value -> if (resultCode == RESULT_OK) {
                val counterToUpdate = counterList[editingIndex]

                val name = data!!.getStringExtra("name")
                val dateTime = data.getSerializableExtra("dateTime") as DateTime
                counterToUpdate.name = name
                counterToUpdate.dateTime = dateTime

                adapter.notifyDataSetChanged()
            }

            ActivityRequest.CreateListItem.value -> if (resultCode == RESULT_OK) {
                //add the new item

                val name = data!!.getStringExtra("name")
                val dateTime = data.getSerializableExtra("dateTime") as DateTime
                val newCounter = DayCounter(name = name, dateTime = dateTime)

                counterList.add(newCounter)

                saveChanges()
            }
        }
    }

    private fun loadSave(): MutableList<DayCounter> {
        val prefs = getPreferences(MODE_PRIVATE)
        val json = prefs.getString("counter-list-json", null) ?: return sampleData()
        //Log.d("daycounter", "loading: $json")

        val list = gson.fromJson<MutableList<DayCounter>>(json, object : TypeToken<MutableList<DayCounter>>() {}.type)

        return list
    }

    private fun saveChanges() {
        val prefs = getPreferences(MODE_PRIVATE)
        val edit = prefs.edit()

        val json = gson.toJson(counterList)

        edit.putString("counter-list-json", json)
        //Log.d("daycounter", "saving: $json")

        edit.apply()
    }

    var selectedItem = -1

    inner class FabCreateNewClickListener : View.OnClickListener {
        override fun onClick(view: View) {
            val intent = Intent(this@MainActivity, NewCounterActivity::class.java)
            startActivityForResult(intent, ActivityRequest.CreateListItem.value)
        }
    }

    inner class ListClickListener : RecyclerTouchListener.ClickListener {
        override fun onClick(view: View, position: Int) {
            val intent = Intent(this@MainActivity, NewCounterActivity::class.java)
            editingIndex = position
            val counter = counterList[position]

            intent.putExtra("name", counter.name)
            intent.putExtra("dateTime", counter.dateTime)

            startActivityForResult(intent, ActivityRequest.EditListItem.value)
        }

        override fun onLongClick(view: View, position: Int) {
            selectedItem = position
            openContextMenu(view)
        }
    }
}

data class DayCounter(var name: String, var dateTime: DateTime)
