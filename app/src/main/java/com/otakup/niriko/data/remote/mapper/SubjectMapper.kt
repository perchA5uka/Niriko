package com.otakup.niriko.data.remote.mapper

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.bangumi.dto.CharacterDto
import com.otakup.niriko.data.remote.bangumi.dto.EpisodeDto
import com.otakup.niriko.data.remote.bangumi.dto.SearchResponseDto
import com.otakup.niriko.data.remote.bangumi.dto.StaffDto
import com.otakup.niriko.data.remote.bangumi.dto.SubjectDetailResponseDto
import com.otakup.niriko.data.remote.bangumi.dto.toEntity

class SubjectMapper {

    fun fromSearchResponse(response: SearchResponseDto): List<SubjectEntity> {
        return response.data.map { it.toEntity() }
    }

    fun fromDetailResponse(response: SubjectDetailResponseDto): SubjectEntity {
        return response.toEntity()
    }

    fun mapCharacters(dtos: List<CharacterDto>): List<CharacterInfo> {
        return dtos.map { dto ->
            CharacterInfo(
                id = dto.id,
                name = dto.name,
                nameCn = dto.nameCn,
                roleName = dto.roleName,
                // 角色头像用 grid 尺寸（Bangumi 服务端裁好的 75x75 正方形），
                // 避免长立绘（高度≫宽度）在 Crop 裁中心时裁到身体中段
                imageUrl = dto.images?.grid ?: dto.images?.small,
                actors = dto.actors.map { mapStaff(it) },
            )
        }
    }

    fun mapStaff(dtos: List<StaffDto>): List<StaffInfo> {
        return dtos.map { mapStaff(it) }
    }

    private fun mapStaff(dto: StaffDto): StaffInfo {
        return StaffInfo(
            id = dto.id,
            name = dto.name,
            nameCn = dto.nameCn,
            roleName = dto.roleName,
            // 人物头像用 grid 正方形，避免长立绘在 Crop 时裁到身体中段
            imageUrl = dto.images?.grid ?: dto.images?.small,
        )
    }

    fun mapEpisodes(dtos: List<EpisodeDto>): List<EpisodeInfo> {
        return dtos.map { dto ->
            EpisodeInfo(
                id = dto.id,
                name = dto.name,
                nameCn = dto.nameCn,
                desc = dto.desc,
                ep = dto.ep,
                sort = dto.sort,
                airdate = dto.airdate,
                duration = dto.duration,
                status = dto.status,
                comment = dto.comment,
                disc = dto.disc,
                durationSeconds = dto.durationSeconds,
                type = dto.type,
            )
        }
    }

    fun mapType(bangumiType: Int): SubjectType {
        return com.otakup.niriko.data.remote.bangumi.dto.mapSubjectType(bangumiType)
    }
}