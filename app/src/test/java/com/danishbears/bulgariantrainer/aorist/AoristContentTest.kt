package com.danishbears.bulgariantrainer.aorist

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AoristContentTest {
    private fun source()=javaClass.classLoader!!.getResourceAsStream("bulgarian_aorist_40.json")!!.use{it.readBytes()}
    private fun root()=AoristContent.strict(source().toString(Charsets.UTF_8)).jsonObject
    private fun errors(value:JsonObject)=AoristContent.parse(value.toString().toByteArray()).issues
    @Test fun originalAttachmentHasFortyDistinctVerbsAllSixExplicitSlotsAndSourceMetadata() {
        assertEquals("af92873c06a36c81973206cc24808d01e1e9cc2cdacce66225f92f095c521e33",AoristContent.hash(source()))
        val d=AoristContent.parse(source());assertTrue(d.issues.toString(),d.issues.isEmpty());assertEquals(40,d.entries.size)
        assertEquals(40,d.entries.map{it["id"]}.distinct().size);assertEquals(1L,AoristContent.revision(d.metadata))
        assertEquals("bg-aorist-core-40",AoristContent.text(d.metadata,"datasetId"));assertEquals(5,d.metadata.getValue("notes").jsonArray.size)
        assertEquals(240,d.entries.sumOf{e->AoristContent.persons.sumOf{AoristContent.answers(e,it).size}})
        assertEquals(listOf("четох"),AoristContent.answers(d.entries.single{it["lemma"]==JsonPrimitive("чета")},"1sg"))
        assertEquals(listOf("прочетох"),AoristContent.answers(d.entries.single{it["lemma"]==JsonPrimitive("прочета")},"1sg"))
        val reflexive=d.entries.single{it["id"]==JsonPrimitive("bgaor-029")}
        assertEquals(listOf("облече се"),AoristContent.answers(reflexive,"2sg"));assertEquals(AoristContent.answers(reflexive,"2sg"),AoristContent.answers(reflexive,"3sg"))
        assertEquals("БЕРОН",AoristContent.text(reflexive.getValue("source").jsonObject,"title"))
    }
    @Test fun bomWhitespaceCyrillicAndPropertyOrderingPreserveEveryField() {
        val r=JsonObject(root().entries.reversed().associate{it.key to it.value})
        val d=AoristContent.parse(("\uFEFF \n"+r.toString()+"\n").toByteArray());assertTrue(d.issues.isEmpty())
        assertEquals(AoristContent.canonical(root()),AoristContent.canonical(AoristContent.document(d.metadata,d.entries)))
    }
    @Test fun unsupportedVersionWrongMarkerDuplicateIdsAndUnknownFieldsAreDiagnosticErrors() {
        val r=root();val e=r.getValue("entries").jsonArray.first()
        for(value in listOf(JsonObject(r+("formatVersion" to JsonPrimitive(2))),JsonObject(r+("formatVersion" to JsonPrimitive("1"))),
            JsonObject(r+("format" to JsonPrimitive("bulgariantrainer.aspect-pairs"))),JsonObject(r+("future" to JsonPrimitive(true))),
            JsonObject(r+("entries" to JsonArray(listOf(e,e)))),JsonObject(r+("datasetRevision" to JsonPrimitive(0)))))assertTrue(errors(value).isNotEmpty())
        assertTrue(errors(JsonObject(r+("entries" to JsonArray(listOf(e,e))))).any{it.path=="$.entries[1].id"})
    }
    @Test fun allSlotsAreMandatoryAndNonemptyAndAlternativesRemainOrderedPerPerson() {
        val e=root().getValue("entries").jsonArray.first().jsonObject;val forms=e.getValue("forms").jsonObject
        for(person in AoristContent.persons)for(value in listOf(JsonObject(forms-person),JsonObject(forms+(person to JsonArray(emptyList()))),
            JsonObject(forms+(person to JsonArray(listOf(JsonPrimitive(" "))))),JsonObject(forms+(person to JsonPrimitive("гледах"))))) {
            assertTrue(AoristContent.validate(JsonObject(e+("forms" to value))).any{it.path.startsWith("entry.forms")})
        }
        val alternatives=JsonObject(e+("forms" to JsonObject(forms+("1sg" to JsonArray(listOf(JsonPrimitive("гледах"),JsonPrimitive("одобрен синтетичен вариант")))))))
        assertTrue(AoristContent.validate(alternatives).isEmpty());assertEquals(2,AoristContent.answers(alternatives,"1sg").size)
        val duplicate=JsonObject(e+("forms" to JsonObject(forms+("1sg" to JsonArray(listOf(JsonPrimitive("гледах"),JsonPrimitive(" ГЛЕДАХ ")))))))
        assertTrue(AoristContent.validate(duplicate).any{it.path.contains("1sg[1]")})
    }
    @Test fun malformedUtf8JsonDuplicateKeysTrailingGarbageAndOversizeNeverParseAsValidContent() {
        val bytes=source()
        for(b in listOf(byteArrayOf(0xd0.toByte()),bytes.copyOf(50),bytes+"garbage".toByteArray(),
            bytes.toString(Charsets.UTF_8).replace("\"formatVersion\": 1","\"formatVersion\": 1, \"formatVersion\": 1").toByteArray(),
            ByteArray(AoristContent.MAX_BYTES+1))) {
            try{AoristContent.parse(b);fail("invalid document")}catch(_:AoristException){}
        }
    }
    @Test fun normalizedDuplicateKeysPreserveBulgarianLettersParticlesAndLiteralSpelling() {
        assertEquals("четох",AoristContent.key(" \tЧЕ\u0301ТОХ\n"))
        assertEquals("облякох се",AoristContent.key("ОБЛЯКОХ\u00a0\u2003СЕ"))
        assertEquals("ѝ",AoristContent.key("и\u0300"));assertEquals("й",AoristContent.key("и\u0306"))
        assertEquals(3,listOf("и","й","ѝ").map(AoristContent::key).distinct().size)
        assertNotEquals(AoristContent.key("облякох се"),AoristContent.key("облякох си"))
        assertNotEquals(AoristContent.key("облякох се"),AoristContent.key("облякох"))
        assertNotEquals(AoristContent.key("чете"),AoristContent.key("четe"))
    }
}
