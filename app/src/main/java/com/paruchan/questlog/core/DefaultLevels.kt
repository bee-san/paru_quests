package com.paruchan.questlog.core

object DefaultLevels {
    fun paruchan(): List<Level> = listOf(
        Level(
            level = 1,
            xpRequired = 0,
            title = "Babsy Paru",
            unlocks = listOf("Quest log awakened"),
        ),
        Level(
            level = 2,
            xpRequired = 120,
            title = "Babsy",
            unlocks = listOf("Tiny treat pouch unlocked"),
        ),
        Level(
            level = 3,
            xpRequired = 320,
            title = "Tiny Paru",
            unlocks = listOf("Mini side quest sparkle"),
        ),
        Level(
            level = 4,
            xpRequired = 700,
            title = "Cozy Paru",
            unlocks = listOf("Blanket fort planning rights"),
        ),
        Level(
            level = 5,
            xpRequired = 1250,
            title = "Snacky Paru",
            unlocks = listOf("Rare snack-tier quest rewards"),
        ),
        Level(
            level = 6,
            xpRequired = 2200,
            title = "Brave Babsy",
            unlocks = listOf("Daily courage squeak"),
        ),
        Level(
            level = 7,
            xpRequired = 3550,
            title = "Visa Bard of Babsy",
            unlocks = listOf("Paperwork victory anthem"),
        ),
        Level(
            level = 8,
            xpRequired = 4700,
            title = "Starry Paru",
            unlocks = listOf("Future quest chain planning"),
        ),
        Level(
            level = 9,
            xpRequired = 6200,
            title = "Royal Babsy",
            unlocks = listOf("Legendary reward cache"),
        ),
        Level(
            level = 10,
            xpRequired = 8200,
            title = "Dreamy Paruchan",
            unlocks = listOf("Hall of tiny triumphs"),
        ),
        Level(
            level = 11,
            xpRequired = 10800,
            title = "Paru Quest Hero",
            unlocks = listOf("Heroic quest ribbon"),
        ),
        Level(
            level = 12,
            xpRequired = 14000,
            title = "Legendary Babsy Paru",
            unlocks = listOf("Hall of soft triumphs"),
        ),
    )

    fun isLegacyDefault(levels: List<Level>): Boolean =
        levels.sameLadderAs(legacyParuchan())

    private fun legacyParuchan(): List<Level> = listOf(
        Level(
            level = 1,
            xpRequired = 0,
            title = "Pocket Questling",
            unlocks = listOf("Quest log awakened"),
        ),
        Level(
            level = 2,
            xpRequired = 150,
            title = "Snack Squire",
            unlocks = listOf("Tiny treats count double emotionally"),
        ),
        Level(
            level = 3,
            xpRequired = 450,
            title = "Errand Enchanter",
            unlocks = listOf("One ceremonial side quest slot"),
        ),
        Level(
            level = 4,
            xpRequired = 900,
            title = "Cozy Crusader",
            unlocks = listOf("Blanket fort planning rights"),
        ),
        Level(
            level = 5,
            xpRequired = 1500,
            title = "Charm Collector",
            unlocks = listOf("Rare sticker-tier quest rewards"),
        ),
        Level(
            level = 6,
            xpRequired = 2400,
            title = "Babsy Minstrel",
            unlocks = listOf("Daily ballad of completed chores"),
        ),
        Level(
            level = 7,
            xpRequired = 3550,
            title = "Visa Bard of Babsy",
            unlocks = listOf("Paperwork victory anthem"),
        ),
        Level(
            level = 8,
            xpRequired = 5000,
            title = "Calendar Paladin",
            unlocks = listOf("Future quest chain planning"),
        ),
        Level(
            level = 9,
            xpRequired = 7000,
            title = "Treat Hoarder",
            unlocks = listOf("Legendary reward cache"),
        ),
        Level(
            level = 10,
            xpRequired = 9500,
            title = "Paruchan Legend",
            unlocks = listOf("Hall of tiny triumphs"),
        ),
    )

    private fun List<Level>.sameLadderAs(other: List<Level>): Boolean {
        if (size != other.size) return false
        return zip(other).all { (left, right) ->
            left.level == right.level &&
                left.xpRequired == right.xpRequired &&
                left.title == right.title &&
                left.unlocks == right.unlocks
        }
    }
}
