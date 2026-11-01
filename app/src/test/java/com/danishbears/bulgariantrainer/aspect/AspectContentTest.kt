package com.danishbears.bulgariantrainer.aspect

import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*

class AspectContentTest{
    private fun bytes()=javaClass.classLoader!!.getResourceAsStream("bulgarian_aspect_pairs_100.json")!!.use{it.readBytes()}
    @Test fun pinnedDatasetAndBomPreserveEveryAnswerExampleAndMetadata(){
        val normal=AspectContent.parse(bytes());val bom=AspectContent.parse(byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte())+bytes())
        assertTrue(normal.issues.isEmpty());assertEquals(normal,bom);assertEquals(100,normal.entries.size)
        assertEquals(212,normal.entries.sumOf{AspectContent.answers(it,"acceptedImperfective").size+AspectContent.answers(it,"acceptedPerfective").size})
        assertEquals(16,normal.entries.count{it["examples"]!=null})
    }
    @Test fun normalizationMatchesLiteralWebOrderAndKeepsBulgarianLettersParticlesAndPunctuation(){
        assertEquals("купя",AspectContent.key(" \tКУ\u0301ПЯ\u00a0\n"))
        assertEquals("ѝ",AspectContent.key("и\u0300"));assertEquals("й",AspectContent.key("и\u0306"))
        assertNotEquals(AspectContent.key("и"),AspectContent.key("ѝ"));assertNotEquals(AspectContent.key("и"),AspectContent.key("й"))
        assertNotEquals(AspectContent.key("опитам"),AspectContent.key("опитам се"))
        assertNotEquals(AspectContent.key("купя"),AspectContent.key("kупя"))
        assertNotEquals(AspectContent.key("купя"),AspectContent.key("купя!"))
        assertEquals("ѐ",AspectContent.key("е\u0300"))
    }
    @Test fun structuralJsonFailuresCannotBeCoercedOrSilentlyDropKeys(){
        for(raw in listOf("{\"format\":1,\"format\":2}","{} true","[".repeat(10000)+"0"+"]".repeat(10000))){
            try{AspectContent.parse(raw.toByteArray());fail("accepted malformed structure")}catch(_:AspectException){}
        }
        try{AspectContent.parse(byteArrayOf(0xc0.toByte(),0xaf.toByte()));fail("invalid UTF8")}catch(_:AspectException){}
        try{AspectContent.parse(ByteArray(AspectContent.MAX_BYTES+1));fail("oversize")}catch(_:AspectException){}
    }
    @Test fun requiredTypesPrimaryMembershipAndNormalizedDuplicatesAreExplicitErrors(){
        val entry=AspectContent.parse(bytes()).entries.first()
        val variants=listOf(
            entry-"topic",
            JsonObject(entry+("acceptedImperfective" to JsonArray(listOf(JsonPrimitive("друго"))))),
            JsonObject(entry+("acceptedImperfective" to JsonArray(listOf(JsonPrimitive("вземам"),JsonPrimitive(" ВЗЕМАМ "))))),
            JsonObject(entry+("acceptedPerfective" to JsonArray(listOf(JsonPrimitive(17))))),
            JsonObject(entry+("studyStage" to JsonPrimitive("1"))),
            JsonObject(entry+("unexpected" to JsonPrimitive("never discard")))
        )
        for(e in variants)assertTrue(AspectContent.validate(JsonObject(e)).isNotEmpty())
    }
    @Test fun stagesAndRelationsAreIndependentAndEmptyNotesOptionalExamplesAreValid(){
        val entry=AspectContent.parse(bytes()).entries.first()
        val changed=JsonObject(entry+mapOf("studyStage" to JsonPrimitive(3),"relationType" to JsonPrimitive("core"),"note" to JsonPrimitive(""),"examples" to JsonNull))
        assertTrue(AspectContent.validate(changed).isEmpty())
    }
}
