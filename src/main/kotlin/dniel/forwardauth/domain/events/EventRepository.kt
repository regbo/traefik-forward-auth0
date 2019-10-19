package dniel.forwardauth.domain.events

import org.springframework.stereotype.Component

/**
 * Simple in-memory repository to hold events.
 * Should have a max limit and/or auto-expire items.
 */
@Component()
class EventRepository {

    private val events = ArrayList<Event>()

    fun all(): List<Event> {
        return events
    }

    fun put(event: Event) {
        events.add(event)
    }
}