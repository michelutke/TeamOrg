package ch.teamorg.fake

import ch.teamorg.domain.CreateEventRequest
import ch.teamorg.domain.EditEventRequest
import ch.teamorg.domain.Event
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.SubGroup
import ch.teamorg.repository.EventRepository

class FakeEventRepository : EventRepository {

    var getEventDetailResult: Result<EventWithTeams> = Result.failure(NotImplementedError())
    var getMyEventsResult: Result<List<EventWithTeams>> = Result.success(emptyList())
    var createEventResult: Result<Event> = Result.failure(NotImplementedError())
    var editEventResult: Result<Event> = Result.failure(NotImplementedError())
    var cancelEventResult: Result<Unit> = Result.success(Unit)
    var uncancelEventResult: Result<Unit> = Result.success(Unit)
    var subGroupsByTeam: Map<String, List<SubGroup>> = emptyMap()

    val createRequests = mutableListOf<CreateEventRequest>()
    val editCalls = mutableListOf<Pair<String, EditEventRequest>>()
    val cancelCalls = mutableListOf<Pair<String, String>>()
    val uncancelCalls = mutableListOf<Pair<String, String>>()
    var getEventDetailCallCount = 0

    override suspend fun getMyEvents(from: String?, to: String?, type: String?, teamId: String?): Result<List<EventWithTeams>> =
        getMyEventsResult

    override suspend fun getEventDetail(id: String): Result<EventWithTeams> {
        getEventDetailCallCount++
        return getEventDetailResult
    }

    override suspend fun createEvent(request: CreateEventRequest): Result<Event> {
        createRequests += request
        return createEventResult
    }

    override suspend fun editEvent(id: String, request: EditEventRequest): Result<Event> {
        editCalls += id to request
        return editEventResult
    }

    override suspend fun cancelEvent(id: String, scope: String): Result<Unit> {
        cancelCalls += id to scope
        return cancelEventResult
    }

    override suspend fun uncancelEvent(id: String, scope: String): Result<Unit> {
        uncancelCalls += id to scope
        return uncancelEventResult
    }

    override suspend fun duplicateEvent(id: String): Result<Event> = Result.failure(NotImplementedError())

    override suspend fun getSubGroups(teamId: String): Result<List<SubGroup>> =
        Result.success(subGroupsByTeam[teamId] ?: emptyList())
}
