package ru.netology.nework

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import ru.netology.nework.data.api.NeWorkApi
import ru.netology.nework.data.api.dto.PostDto
import ru.netology.nework.data.api.dto.toDomain
import ru.netology.nework.data.api.dto.toDto
import ru.netology.nework.domain.model.Coordinates
import ru.netology.nework.domain.model.JobCreateRequest
import ru.netology.nework.domain.model.Post

class ApiContractTest {
    @Test
    fun coordinatesUseServerFieldNames() {
        val post = Post(11, content = "Test", coords = Coordinates(55.75, 37.62))
        val json = Gson().toJsonTree(post.toDto()).asJsonObject.getAsJsonObject("coords")
        assertEquals(55.75, json.get("lat").asDouble, 0.00001)
        assertEquals(37.62, json.get("long").asDouble, 0.00001)
        assertFalse(json.has("latitude"))
        assertFalse(json.has("longitude"))
    }

    @Test
    fun mentionsReadNamesFromServerUsersMap() {
        val json = """{"id":11,"authorId":7,"content":"Test","published":"2026-10-01T00:00:00Z",
            "mentionIds":[9],"likeOwnerIds":[],"users":{"9":{"name":"Anna","avatar":null}}}"""
        val post = Gson().fromJson(json, PostDto::class.java).toDomain()
        assertEquals("Anna", post.users[9]?.name)
    }

    @Test
    fun jobRequestPreservesExistingId() {
        val request = JobCreateRequest("Company", "Developer", "2024-01-01T00:00:00Z", id = 31)
        assertEquals(31L, Gson().toJsonTree(request.toDto()).asJsonObject.get("id").asLong)
    }

    @Test
    fun endpointsMatchSwagger() {
        val methods = NeWorkApi::class.java.declaredMethods.associateBy { it.name }
        assertEquals("{id}/wall", methods.getValue("getWall").getAnnotation(GET::class.java).value)
        assertEquals("{id}/jobs", methods.getValue("getJobs").getAnnotation(GET::class.java).value)
        assertEquals("posts", methods.getValue("savePost").getAnnotation(POST::class.java).value)
        assertEquals("events", methods.getValue("saveEvent").getAnnotation(POST::class.java).value)
        assertEquals("my/jobs", methods.getValue("createJob").getAnnotation(POST::class.java).value)
        val parameters = methods.getValue("authenticate").parameterAnnotations
        assertEquals("login", parameters[0].filterIsInstance<Query>().single().value)
        assertEquals("pass", parameters[1].filterIsInstance<Query>().single().value)
    }
}

