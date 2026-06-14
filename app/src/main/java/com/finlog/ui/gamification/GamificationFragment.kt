package com.finlog.ui.gamification

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.finlog.databinding.FragmentGamificationBinding
import com.finlog.databinding.ItemBadgeBinding

class GamificationFragment : Fragment() {
    private var _b: FragmentGamificationBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentGamificationBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        val ctx = requireContext()
        val streak = GamificationManager.getStreak(ctx)
        val points = GamificationManager.getPoints(ctx)
        val level = GamificationManager.getLevelName(points)

        b.tvStreak.text = "🔥 $streak Day Streak"
        b.tvPoints.text = "$points Points"
        b.tvLevel.text = level

        b.rvBadges.layoutManager = GridLayoutManager(ctx, 2)
        b.rvBadges.adapter = BadgeAdapter(GamificationManager.getAllBadges(ctx))
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class BadgeAdapter(private val list: List<Badge>) : RecyclerView.Adapter<BadgeAdapter.VH>() {
    inner class VH(val b: ItemBadgeBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(ItemBadgeBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) {
        val badge = list[pos]
        h.b.tvName.text = badge.name
        h.b.tvEmoji.text = badge.emoji
        h.b.tvDescription.text = badge.description

        if (badge.isEarned) {
            h.b.tvEmoji.alpha = 1.0f
            h.b.ivLock.visibility = View.GONE
            h.b.root.alpha = 1.0f
        } else {
            h.b.tvEmoji.alpha = 0.3f
            h.b.ivLock.visibility = View.VISIBLE
            h.b.root.alpha = 0.6f
        }
    }
    override fun getItemCount() = list.size
}
