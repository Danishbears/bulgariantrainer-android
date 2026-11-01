package com.danishbears.bulgariantrainer.aspect

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AspectDocuments(private val context:Context){
    suspend fun read(uri:Uri):Pair<String,ByteArray> = withContext(Dispatchers.IO){
        val resolver=context.contentResolver
        val filename=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0) else null}?:"aspect-pairs.json"
        val bytes=resolver.openInputStream(uri)?.use{it.readBytesBounded()}?:throw AspectException("Не удалось открыть документ.")
        filename to bytes
    }
    suspend fun sample():Pair<String,ByteArray> = withContext(Dispatchers.IO){
        "bulgarian_aspect_pairs_100.json" to context.assets.open("bulgarian_aspect_pairs_100.json").use{it.readBytesBounded()}
    }
    private fun java.io.InputStream.readBytesBounded():ByteArray{
        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){
            val read=read(buffer,0,minOf(buffer.size,AspectContent.MAX_BYTES+1-output.size()))
            if(read<0)break
            if(read>0)output.write(buffer,0,read)
            if(output.size()>AspectContent.MAX_BYTES)throw AspectException("Максимум — 1 МиБ.")
        }
        return output.toByteArray()
    }
    suspend fun write(uri:Uri,bytes:ByteArray)=withContext(Dispatchers.IO){
        val stream=context.contentResolver.openOutputStream(uri,"wt")?:throw AspectException("Не удалось создать документ.")
        try{stream.use{it.write(bytes);it.flush()}}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){throw AspectException("Записать файл полностью не удалось. Он может быть неполным; создайте новый документ.")}
    }
}
