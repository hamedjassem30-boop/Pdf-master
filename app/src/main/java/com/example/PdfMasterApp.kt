package com.example

import android.app.Application
import com.example.data.PdfMasterRepository
import com.example.data.PreferencesRepository
import com.example.data.local.AppDatabase
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfMasterApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var pdfMasterRepository: PdfMasterRepository
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        database = AppDatabase.getInstance(applicationContext)
        preferencesRepository = PreferencesRepository(applicationContext)
        pdfMasterRepository = PdfMasterRepository(applicationContext, database.pdfMasterDao())
    }
}
