package com.paruchan.questlog.core

object DefaultLevels {
    fun paruchan(): List<Level> = listOf(
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
}
