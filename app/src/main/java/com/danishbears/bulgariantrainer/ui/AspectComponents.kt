package com.danishbears.bulgariantrainer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.aspect.AspectContent
import com.danishbears.bulgariantrainer.pronunciation.PronunciationTarget
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AspectTopBar(title:String,onBack:()->Unit,onHelp:(()->Unit)?=null,enabled:Boolean=true) {
    TopAppBar(title={Text(title,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.semantics{heading()})},windowInsets=WindowInsets(0,0,0,0),navigationIcon={
        IconButton(onClick=onBack,enabled=enabled){Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.back))}
    },actions={onHelp?.let{IconButton(onClick=it,enabled=enabled){Icon(Icons.Default.Info,stringResource(R.string.aspect_help_title))}}})
}

/** One labelled checkbox semantics node and a wrapping label, including at large font scales. */
@Composable internal fun AspectCheckbox(label:String,checked:Boolean,enabled:Boolean=true,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).semantics(mergeDescendants=true){}
        .toggleable(checked,enabled=enabled,role=Role.Checkbox,onValueChange=onChange),verticalAlignment=Alignment.CenterVertically) {
        Checkbox(checked,onCheckedChange=null,enabled=enabled)
        Text(label,Modifier.weight(1f).padding(vertical=8.dp))
    }
}

@Composable internal fun AspectHeading(text:String){Text(text,style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics{heading()})}
@Composable internal fun AspectNotice(text:String,error:Boolean=false) {
    Text(text,color=if(error)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
}
@Composable internal fun aspectRelationLabel(value:String)=stringResource(when(value){
    "core"->R.string.aspect_relation_core;"resultative"->R.string.aspect_relation_resultative;else->R.string.aspect_relation_bounded
})

/** Only call in management or with an already-revealed feedback snapshot. Never accepts learner input. */
@Composable internal fun AspectEntry(entry:JsonObject,speechOwner:String?=null) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        for((form,meaning,accepted) in listOf(Triple("imperfective","russianImperfective","acceptedImperfective"),Triple("perfective","russianPerfective","acceptedPerfective"))) {
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if(form=="imperfective")R.string.aspect_imperfective else R.string.aspect_perfective),style=MaterialTheme.typography.labelLarge)
                Text(AspectContent.string(entry,form)+" — "+AspectContent.string(entry,meaning),style=MaterialTheme.typography.titleMedium)
                speechOwner?.let{PronunciationPlayback(PronunciationTarget("$it-$form",AspectContent.string(entry,form)))}
                Text(stringResource(if(form=="imperfective")R.string.aspect_accepted_imperfective else R.string.aspect_accepted_perfective))
                AspectContent.answers(entry,accepted).forEachIndexed{i,answer->
                    Text(answer)
                    if(AspectContent.key(answer)!=AspectContent.key(AspectContent.string(entry,form)))speechOwner?.let{PronunciationPlayback(PronunciationTarget("$it-$accepted-$i",answer))}
                }
            }
        }
        AspectContent.string(entry,"note").takeIf{it.isNotBlank()}?.let{Text(stringResource(R.string.aspect_note),style=MaterialTheme.typography.labelLarge);Text(it)}
        (entry["examples"] as? JsonObject)?.let{examples->
            Text(stringResource(R.string.aspect_examples),style=MaterialTheme.typography.titleMedium)
            for(side in listOf("imperfective","perfective")) {
                val example=examples.getValue(side).jsonObject
                Text(stringResource(if(side=="imperfective")R.string.aspect_imperfective else R.string.aspect_perfective),style=MaterialTheme.typography.labelLarge)
                Text(AspectContent.string(example,"bg"));Text(AspectContent.string(example,"ru"))
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.aspect_topic)+": "+AspectContent.string(entry,"topic"))
        Text(stringResource(R.string.ap_stage,entry.getValue("studyStage").jsonPrimitive.int)+" · "+aspectRelationLabel(AspectContent.string(entry,"relationType")))
        val source=entry.getValue("provenance").jsonObject
        Text(stringResource(if(source["kind"]==JsonPrimitive("presentation"))R.string.aspect_origin_presentation else R.string.aspect_origin_added))
        if(source["kind"]==JsonPrimitive("presentation"))Text(stringResource(R.string.aspect_source_context,AspectContent.string(source,"sourceId"),source.getValue("tableRow").jsonPrimitive.int))
        Text(stringResource(R.string.aspect_external_id)+": "+AspectContent.string(entry,"id"),style=MaterialTheme.typography.bodySmall)
    }
}
