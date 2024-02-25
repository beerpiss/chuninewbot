package nadinenathaniel.kord.extensions.pagination

import com.kotlindiscord.kord.extensions.checks.types.CheckWithCache
import com.kotlindiscord.kord.extensions.components.ComponentContainer
import com.kotlindiscord.kord.extensions.components.buttons.PublicInteractionButton
import com.kotlindiscord.kord.extensions.components.publicButton
import com.kotlindiscord.kord.extensions.components.types.emoji
import com.kotlindiscord.kord.extensions.pagination.EXPAND_EMOJI
import com.kotlindiscord.kord.extensions.pagination.FIRST_PAGE_EMOJI
import com.kotlindiscord.kord.extensions.pagination.LAST_PAGE_EMOJI
import com.kotlindiscord.kord.extensions.pagination.LEFT_EMOJI
import com.kotlindiscord.kord.extensions.pagination.RIGHT_EMOJI
import com.kotlindiscord.kord.extensions.pagination.SWITCH_EMOJI
import com.kotlindiscord.kord.extensions.utils.MutableStringKeyedMap
import com.kotlindiscord.kord.extensions.utils.capitalizeWords
import com.kotlindiscord.kord.extensions.utils.scheduling.Scheduler
import com.kotlindiscord.kord.extensions.utils.scheduling.Task
import dev.kord.common.entity.ButtonStyle
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.event.interaction.ComponentInteractionCreateEvent
import kotlinx.coroutines.runBlocking
import nadinenathaniel.kord.extensions.pagination.builders.PageTransitionCallback
import nadinenathaniel.kord.extensions.pagination.pages.Pages
import java.util.Locale

/**
 * Abstract class containing some common functionality needed by interactive button-based paginators.
 */
abstract class BaseButtonPaginator(
    pages: Pages,
    owner: UserBehavior? = null,
    timeoutSeconds: Long? = null,
    keepEmbed: Boolean = true,
    switchEmoji: ReactionEmoji = if (pages.groups.size == 2) EXPAND_EMOJI else SWITCH_EMOJI,
    mutator: PageTransitionCallback? = null,
    bundle: String? = null,
    locale: Locale? = null,
) : BasePaginator(pages, owner, timeoutSeconds, keepEmbed, switchEmoji, mutator, bundle, locale) {
    /** [ComponentContainer] instance managing the buttons for this paginator. **/
    open var components: ComponentContainer = ComponentContainer()

    /** Scheduler used to schedule the paginator's timeout. **/
    var scheduler: Scheduler = Scheduler()

    /** Scheduler used to schedule the paginator's timeout. **/
    var task: Task? = if (timeoutSeconds != null) {
        runBlocking { // This is a trivially quick block, so it should be fine.
            scheduler.schedule(timeoutSeconds) { destroy() }
        }
    } else {
        null
    }

    private val lastRowNumber by lazy { components.rows.size - 1 }
    private val secondRowNumber = 1

    /** Button builder representing the button that switches to the first page. **/
    open var firstPageButton: PublicInteractionButton<*>? = null

    /** Button builder representing the button that switches to the previous page. **/
    open var backButton: PublicInteractionButton<*>? = null

    /** Button builder representing the button that switches to the next page. **/
    open var nextButton: PublicInteractionButton<*>? = null

    /** Button builder representing the button that switches to the last page. **/
    open var lastPageButton: PublicInteractionButton<*>? = null

    /** Button builder representing the button that switches between groups. **/
    open var switchButton: PublicInteractionButton<*>? = null

    /** Group-specific buttons, if any. **/
    open val groupButtons: MutableStringKeyedMap<PublicInteractionButton<*>> = mutableMapOf()

    /** Whether it's possible for us to have a row of group-switching buttons. **/
    @Suppress("MagicNumber")
    val canUseSwitchingButtons: Boolean by lazy { allGroups.size in 3..5 && "" !in allGroups }

    /** A button-oriented check function that matches based on the [owner] property. **/
    val defaultCheck: CheckWithCache<ComponentInteractionCreateEvent> = {
        if (!active) {
            fail()
        } else if (owner == null) {
            pass()
        } else if (event.interaction.user.id == owner.id) {
            pass()
        } else {
            fail()
        }
    }

    override suspend fun destroy() {
        runTimeoutCallbacks()
        task?.cancel()
    }

    override suspend fun setup() {
        if (pages.groups.values.any { it.size > 1 }) {
            // Add navigation buttons...
            firstPageButton = components.publicButton {
                deferredAck = true
                style = ButtonStyle.Secondary
                disabled = pages.groups[currentGroup]!!.size <= 1

                check(defaultCheck)

                emoji(FIRST_PAGE_EMOJI)

                action {
                    goToPage(0)

                    send()
                    task?.restart()
                }
            }

            backButton = components.publicButton {
                deferredAck = true
                style = ButtonStyle.Secondary
                disabled = pages.groups[currentGroup]!!.size <= 1

                check(defaultCheck)

                emoji(LEFT_EMOJI)

                action {
                    previousPage()

                    send()
                    task?.restart()
                }
            }

            nextButton = components.publicButton {
                deferredAck = true
                style = ButtonStyle.Secondary
                disabled = pages.groups[currentGroup]!!.size <= 1

                check(defaultCheck)

                emoji(RIGHT_EMOJI)

                action {
                    nextPage()

                    send()
                    task?.restart()
                }
            }

            lastPageButton = components.publicButton {
                deferredAck = true
                style = ButtonStyle.Secondary
                disabled = pages.groups[currentGroup]!!.size <= 1

                check(defaultCheck)

                emoji(LAST_PAGE_EMOJI)

                action {
                    goToPage(pages.groups[currentGroup]!!.size - 1)

                    send()
                    task?.restart()
                }
            }
        }

        /*if (pages.groups.values.any { it.size > 1 } || !keepEmbed) {
            // Add the destroy button
            components.publicButton(lastRowNumber) {
                deferredAck = true

                check(defaultCheck)

                label = if (keepEmbed) {
                    style = ButtonStyle.Primary
                    emoji(FINISH_EMOJI)

                    translate("paginator.button.done")
                } else {
                    style = ButtonStyle.Danger
                    emoji(DELETE_EMOJI)

                    translate("paginator.button.delete")
                }

                action {
                    destroy()
                }
            }
        }*/

        if (pages.groups.size > 1) {
            if (canUseSwitchingButtons) {
                // Add group-switching buttons

                allGroups.forEach { group ->
                    groupButtons[group] = components.publicButton(secondRowNumber) {
                        deferredAck = true
                        label = translate(group).capitalizeWords(localeObj)
                        style = ButtonStyle.Secondary

                        check(defaultCheck)

                        action {
                            switchGroup(group)
                            task?.restart()
                        }
                    }
                }
            } else {
                // Add the singular switch button

                switchButton = components.publicButton(lastRowNumber) {
                    deferredAck = true

                    check(defaultCheck)

                    emoji(switchEmoji)

                    label = if (allGroups.size == 2) {
                        translate("paginator.button.more")
                    } else {
                        translate("paginator.button.group.switch")
                    }

                    action {
                        nextGroup()

                        send()
                        task?.restart()
                    }
                }
            }
        }

        components.sort()
        updateButtons()
    }

    /**
     * Convenience function to switch to a specific group.
     */
    suspend fun switchGroup(group: String) {
        if (group == currentGroup) {
            return
        }

        // To avoid out-of-bounds
        currentPageNum = minOf(currentPageNum, pages.groups[group]!!.size)
        currentPage = pages.get(group, currentPageNum)
        currentGroup = group

        send()
    }

    override suspend fun nextGroup() {
        val current = currentGroup
        val nextIndex = allGroups.indexOf(current) + 1

        if (nextIndex >= allGroups.size) {
            switchGroup(allGroups.first())
        } else {
            switchGroup(allGroups[nextIndex])
        }
    }

    override suspend fun goToPage(page: Int) {
        if (page == currentPageNum) {
            return
        }

        if (page < 0 || page > pages.groups[currentGroup]!!.size - 1) {
            return
        }

        currentPageNum = page
        currentPage = pages.get(currentGroup, currentPageNum)

        send()
    }

    /**
     * Convenience function that enables and disables buttons as necessary, depending on the current page number.
     */
    fun updateButtons() {
        if (currentPageNum <= 0) {
            setDisabledButton(firstPageButton)
            setDisabledButton(backButton)
        } else {
            setEnabledButton(firstPageButton)
            setEnabledButton(backButton)
        }

        if (currentPageNum >= pages.groups[currentGroup]!!.size - 1) {
            setDisabledButton(nextButton)
            setDisabledButton(lastPageButton)
        } else {
            setEnabledButton(nextButton)
            setEnabledButton(lastPageButton)
        }

        if (allGroups.size == 2) {
            if (currentGroup == pages.defaultGroup) {
                switchButton?.label = translate("paginator.button.more")
            } else {
                switchButton?.label = translate("paginator.button.less")
            }
        }

        if (canUseSwitchingButtons) {
            groupButtons.forEach { (key, value) ->
                if (key == currentGroup) {
                    setDisabledButton(value)
                } else {
                    setEnabledButton(value)
                }
            }
        }
    }

    /** Replace an enabled interactive button in [components] with a disabled button of the same ID. **/
    fun setDisabledButton(button: PublicInteractionButton<*>?) {
        button ?: return

        button.disable()
    }

    /** Replace a disabled button in [components] with the given interactive button of the same ID. **/
    fun setEnabledButton(button: PublicInteractionButton<*>?) {
        button ?: return

        button.enable()
    }
}
