from collections.abc import Sequence
from typing import TYPE_CHECKING

import discord
import discord.ui
from discord.ext.commands import Context

from utils import floor_to_ndp
from utils.ranks import rank_icon

from .pagination import PaginationView

if TYPE_CHECKING:
    from api import MusicRecord


class B30View(PaginationView):
    def __init__(
        self,
        ctx: Context,
        items: Sequence["MusicRecord"],
        per_page: int = 3,
        show_average: bool = True,
    ):
        super().__init__(ctx, items, per_page)
        self.average = floor_to_ndp(
            sum(item.play_rating for item in items) / len(items), 2
        )
        self.has_estimated_play_rating = any(item.unknown_const for item in items)
        self.show_average = show_average

    def format_content(self) -> str:
        return (f"Average: **{self.average}**" if self.show_average else "") + (
            "\nPlay ratings marked with asterisks are estimated (due to lack of chart constants)."
            if self.has_estimated_play_rating
            else ""
        )

    def format_page(
        self, items: Sequence["MusicRecord"], start_index: int = 0
    ) -> Sequence[discord.Embed]:
        embeds = []
        for idx, item in enumerate(items):
            embeds.append(
                discord.Embed(
                    description=f"▸ {rank_icon(item.rank)} ▸ {item.score} ▸ **{floor_to_ndp(item.play_rating, 2)}{'' if not item.unknown_const else '*'}**\n",
                    color=item.difficulty.color(),
                )
                .set_author(
                    name=f"{idx + start_index + 1}. {item.title} [{item.displayed_difficulty}]"
                )
                .set_thumbnail(url=item.full_jacket_url())
            )
        embeds.append(
            discord.Embed(description=f"Page {self.page + 1}/{self.max_index + 1}")
        )
        return embeds

    async def callback(self, interaction: discord.Interaction):
        begin = self.page * self.per_page
        end = (self.page + 1) * self.per_page
        await interaction.response.edit_message(
            content=self.format_content(),
            embeds=self.format_page(self.items[begin:end], begin),
            view=self,
        )
