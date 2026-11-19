package com.danishbears.bulgariantrainer.aorist

import kotlin.random.Random
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AoristPlannerTest {
    private fun entry()=AoristContent.strict(javaClass.classLoader!!.getResourceAsStream("bulgarian_aorist_40.json")!!.bufferedReader().use{it.readText()}).jsonObject.getValue("entries").jsonArray.first().jsonObject
    private fun forms(values:Map<String,List<String>>)=JsonObject(entry()+("forms" to buildJsonObject{for(p in AoristContent.persons)put(p,JsonArray(values.getValue(p).map(::JsonPrimitive)))}))
    @Test fun storedAlternativesAndNormalizedHomographsAreNeverDistractorsAndCountsDoNotConsumeRandomness() {
        // Explicit synthetic alternatives test algorithm mechanics, not dictionary validity.
        val e=forms(mapOf("1sg" to listOf("гледах","одобрен вариант"),"2sg" to listOf("одобрен вариант"),"3sg" to listOf("ГЛЕ\u0301ДАХ"),
            "1pl" to listOf("гледахме"),"2pl" to listOf("гледахте"),"3pl" to listOf("гледаха")))
        val stored=AoristContent.persons.mapIndexed{i,p->AoristStoredTarget(i+1L,1,p,e)};val target=stored.first()
        val planner=AoristChoicePlanner(Random(19));repeat(10){assertEquals(4,planner.count(target,stored))}
        val options=planner.options(target,stored);val repeat=AoristChoicePlanner(Random(19)).options(target,stored)
        assertEquals(repeat,options);assertEquals(4,options.size)
        val keys=options.map{AoristContent.key(it.text)};assertEquals(4,keys.distinct().size)
        assertEquals(1,keys.count{it in target.answers.map(AoristContent::key)});assertFalse(keys.contains("одобрен вариант"))
        assertEquals(options.indices.map{"o${it+1}"},options.map{it.id})
    }
    @Test fun queuePriorityUniqueTargetsShortSizeAndInjectedRandomnessAreDeterministic() {
        val e=entry();val pool=(1..20).map{AoristCandidate(AoristStoredTarget(it.toLong(),it/6+1L,AoristContent.persons[it%6],e),if(it<=4)0 else 1,0)}
        val first=AoristPlanner(Random(23)).plan(pool,8);assertEquals(first,AoristPlanner(Random(23)).plan(pool,8))
        assertNotEquals(first,AoristPlanner(Random(24)).plan(pool,8));assertTrue(first.map{it.target.formId}.containsAll((1L..4L).toList()))
        assertEquals(8,first.map{it.target.formId}.distinct().size);assertEquals(20,AoristPlanner(Random(23)).plan(pool,100).size)
    }
}
