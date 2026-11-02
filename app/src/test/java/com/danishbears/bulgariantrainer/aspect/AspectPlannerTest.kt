package com.danishbears.bulgariantrainer.aspect

import kotlinx.serialization.json.*
import kotlin.random.Random
import org.junit.Test
import org.junit.Assert.*

class AspectPlannerTest {
    @Test fun goldensExecuteTheActualPinnedWebJavaPlannerAndNormalizer() {
        val text=javaClass.classLoader!!.getResourceAsStream("web_aspect.json")!!.bufferedReader().use{it.readText()}
        val root=Json.parseToJsonElement(text).jsonObject
        for(row in root.getValue("normalization").jsonArray) {
            val r=row.jsonObject
            assertEquals(r.getValue("key").jsonPrimitive.content,AspectContent.key(r.getValue("input").jsonPrimitive.content))
        }
        for(row in root.getValue("planner").jsonArray) {
            val r=row.jsonObject;val expected=r.getValue("expected").jsonObject
            val pool=r.getValue("pool").jsonArray.map{value->val e=value.jsonObject
                AspectCandidate(e.getValue("id").jsonPrimitive.long,e.getValue("forward").takeIf{it!=JsonNull}?.jsonPrimitive?.int,e.getValue("reverse").takeIf{it!=JsonNull}?.jsonPrimitive?.int)}
            val plan=AspectPlanner(Random(9)).plan(pool,r.getValue("size").jsonPrimitive.int,AspectDirection.MIXED)
            assertEquals(expected.getValue("size").jsonPrimitive.int,plan.size)
            assertEquals(plan.size,plan.map{it.id}.toSet().size)
            val forward=plan.count{it.direction==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE}
            assertEquals(expected.getValue("gap").jsonPrimitive.int,kotlin.math.abs(2*forward-plan.size))
            assertEquals(expected.getValue("cost").jsonPrimitive.int,plan.sumOf{q->val p=pool.single{it.id==q.id};if(q.direction==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE)p.forward!! else p.reverse!!})
        }
    }
    @Test fun allSmallPoolsHaveOptimalFeasibleBalanceAndNoRepeatedPair() {
        val planner=AspectPlanner(Random(42))
        for(f in 0..4)for(r in 0..4)for(both in 0..4)for(size in 1..15) {
            val pool=(List(f){0 to null}+List(r){null to 0}+List(both){0 to 0}).mapIndexed{i,p->AspectCandidate(i.toLong(),p.first,p.second)}
            val result=planner.plan(pool,size.coerceAtMost(100),AspectDirection.MIXED)
            val actual=minOf(size,pool.size)
            val best=(0..actual).filter{it<=f+both&&actual-it<=r+both}.minOf{kotlin.math.abs(actual-2*it)}
            assertEquals(actual,result.size);assertEquals(actual,result.map{it.id}.toSet().size)
            assertEquals(best,kotlin.math.abs(actual-2*result.count{it.direction==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE}))
        }
    }
    @Test fun singletonScopeAndOddPriorityPreferDueOnlyAfterFeasibleBalance() {
        val planner=AspectPlanner(Random(1))
        repeat(30) {
            assertEquals(AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,planner.plan(listOf(AspectCandidate(1,0,1)),1,AspectDirection.MIXED).single().direction)
        }
        val forced=planner.plan((1L..7).map{AspectCandidate(it,null,0)},20,AspectDirection.MIXED)
        assertEquals(7,forced.size);assertTrue(forced.all{it.direction==AspectDirection.PERFECTIVE_TO_IMPERFECTIVE})
    }
    @Test fun typedAccuracyUsesWebHalfUpRoundingAndExcludesReveals() {
        val s=AspectPracticeState(1,AspectSettings(),AspectSessionStatus.COMPLETED,20,21,20,16,1,15,4,null,emptyList())
        assertEquals(6.3,s.accuracy!!,0.0)
        assertNull(s.copy(answered=0,correct=0,incorrect=0).accuracy)
    }
}
