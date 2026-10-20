package io.github.nexalloy.nexfusion

import io.github.nexalloy.ApkContext
import io.github.nexalloy.morphe.opcodeEnum
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.result.MethodData
import java.io.File

/**
 * Temporary probe for the Facebook download anchors. Prints what this build exposes so the
 * runtime hooks can be written against real getters instead of guessed names.
 */
class FacebookDownloadProbeTest {
    private val facebookApk = "compat-binaries/com.facebook.katana-583.0.0.39.55.apk"

    @Test
    fun `probe facebook download anchors`() {
        assumeTrue(File(facebookApk).isFile, "Facebook 583 APK is not in compat-binaries/")
        val context = ApkContext(facebookApk)
        context.setupCurrentThread()
        val dexkit = context.dexkit

        fun methodsUsing(strings: List<String>, contains: Boolean = true): List<MethodData> =
            dexkit.findMethod {
                matcher {
                    usingStrings(strings, if (contains) StringMatchType.Contains else StringMatchType.Equals)
                }
            }.distinctBy { it.descriptor }

        println("=== POST MENU FILLERS ===")
        val menuFillers = methodsUsing(listOf("VideoFeedStoryMenuHelper", "DOWNLOAD_VIDEO"))
            .filter { it.returnTypeName == "void" }
        menuFillers.forEach { println("${it.declaredClassName}->${it.name}(${it.paramTypeNames})") }

        println("=== MENU HELPER CLASSES ===")
        methodsUsing(listOf("VideoFeedStoryMenuHelper"))
            .map { it.declaredClassName }
            .distinct()
            .forEach { className ->
                val data = dexkit.getClassData("L$className;")
                println("-- $className")
                data?.methods?.forEach { method ->
                    println("  ${method.name}(${method.paramTypeNames})${method.returnTypeName}")
                }
            }

        println("=== MENU FILLER CLASS METHODS ===")
        val fillerClass = menuFillers.firstOrNull()?.declaredClassName
        if (fillerClass != null) {
            dexkit.findMethod {
                matcher { declaredClass(fillerClass, StringMatchType.Equals, false) }
            }.distinctBy { it.descriptor }.forEach { method ->
                println("  ${method.name}(${method.paramTypeNames})${method.returnTypeName}")
            }
        }

        println("=== GRAPHQL GETTERS ===")
        val getters = listOf(
            "com.facebook.graphql.model.GraphQLStory" to listOf(
                "attachments".hashCode(),
                "attached_story".hashCode(),
            ),
            "com.facebook.graphql.model.GraphQLStoryAttachment" to listOf(
                "media".hashCode(),
                "subattachments".hashCode(),
            ),
            "com.facebook.graphql.model.GraphQLMedia" to listOf(
                "id".hashCode(),
                "playable_url".hashCode(),
                "hd_playable_url".hashCode(),
                "video_hd_url".hashCode(),
                "video_sd_url".hashCode(),
                "dash_manifest".hashCode(),
            ),
        )
        for ((className, hashes) in getters) {
            println("-- $className")
            for (hash in hashes) {
                val found = dexkit.findMethod {
                    matcher {
                        declaredClass(className, StringMatchType.Equals, false)
                        usingNumbers(listOf(hash))
                    }
                }.filter { it.paramTypeNames.isEmpty() }
                found.forEach { method ->
                    println("  hash=$hash ${method.name}()${method.returnTypeName}")
                }
            }
        }

        println("=== PHOTO VIEWER ===")
        val saveActions = dexkit.findMethod {
            matcher { name = "newSavePhotoAction" }
        }.distinctBy { it.descriptor }
        saveActions.forEach { method ->
            println("  saveAction ${method.declaredClassName}->${method.name}(${method.paramTypeNames})${method.returnTypeName}")
            method.instructions.forEach { instruction ->
                val methodRef = instruction.methodRef
                val fieldRef = instruction.fieldRef
                if (methodRef != null || fieldRef != null || instruction.string != null) {
                    println("    call=${methodRef?.name} field=${fieldRef?.name} string=${instruction.string}")
                }
            }
        }
        val mediaGalleryClasses = dexkit.findClass {
            matcher { className("MediaGallery", StringMatchType.Contains) }
        }
        mediaGalleryClasses.forEach { println("  class ${it.name}") }
        val gateMethods = dexkit.findMethod {
            matcher { usingNumbers(listOf("can_viewer_download".hashCode())) }
        }.distinctBy { it.descriptor }
        println("  can_viewer_download methods: ${gateMethods.size}")
        gateMethods.take(10).forEach { println("    ${it.declaredClassName}->${it.name}(${it.paramTypeNames})${it.returnTypeName}") }

        println("=== PHOTO VIEWER MODEL X.DXl ===")
        val photoModel = dexkit.getClassData("LX/DXl;")
        if (photoModel != null) {
            println("-- fields")
            photoModel.fields.take(40).forEach { field ->
                println("  ${field.name}:${field.typeName}")
            }
            println("-- methods")
            photoModel.methods.take(80).forEach { method ->
                println("  ${method.name}(${method.paramTypeNames})${method.returnTypeName}")
            }
        } else {
            println("  class data not found")
        }
        println("-- methods using imageHigh/image hashes")
        for (hash in listOf("imageHigh".hashCode(), "image".hashCode())) {
            dexkit.findMethod {
                matcher { usingNumbers(listOf(hash)) }
            }.filter { it.declaredClassName.contains("DXl") || it.returnTypeName.contains("GraphQL") }
                .distinctBy { it.descriptor }.take(20).forEach { method ->
                    println("  hash=$hash ${method.declaredClassName}->${method.name}(${method.paramTypeNames})${method.returnTypeName}")
                }
        }

        println("=== STORY VIEWER MENU ===")
        val storyMenu = dexkit.getClassData(
            "Lcom/facebook/stories/viewer/ui/buckets/regular/topbar/menu/StoryViewerMoreButtonCallback;",
        )
        if (storyMenu != null) {
            storyMenu.methods.forEach { method ->
                println("  ${method.name}(${method.paramTypeNames})${method.returnTypeName}")
            }
        } else {
            println("  class not found")
        }

        println("=== INSPECT X.bQt->A04 (fds_control_clear_mode) ===")
        val bQt = dexkit.getClassData("LX/bQt;")
        val a04 = bQt?.methods?.firstOrNull { it.name == "A04" }
        if (a04 != null) {
            println("a04 signature: ${a04.name}(${a04.paramTypeNames})${a04.returnTypeName}")
            a04.instructions.forEachIndexed { idx, inst ->
                val m = inst.methodRef
                val f = inst.fieldRef
                val s = inst.string
                println("  [$idx] op=${inst.opcodeEnum} m=${m?.declaredClassName}->${m?.name}(${m?.paramTypeNames?.joinToString(",")}):${m?.returnTypeName} f=${f?.declaredClassName}->${f?.name}:${f?.typeName} s=$s")
            }
        }

        println("=== INSPECT REEL CLASSES ===")
        val cRB = dexkit.getClassData("LX/cRB;")
        if (cRB != null) {
            println("cRB name=${cRB.name}")
            cRB.methods.forEach { println("  cRB.${it.name}(${it.paramTypeNames})${it.returnTypeName}") }
        }
        val clw = dexkit.getClassData("LX/clw;")
        if (clw != null) {
            clw.methods.filter { it.name == "<init>" }.forEach {
                println("  clw.<init>(${it.paramTypeNames})")
            }
        }
        val z3E = dexkit.getClassData("LX/3zE;")
        if (z3E != null) {
            z3E.methods.forEach { println("  3zE.${it.name}(${it.paramTypeNames})${it.returnTypeName}") }
            z3E.fields.forEach { println("  3zE field ${it.name}:${it.typeName}") }
        }
        val fo5 = dexkit.getClassData("LX/5Fo;")
        if (fo5 != null) {
            fo5.methods.take(20).forEach { println("  5Fo.${it.name}(${it.paramTypeNames})${it.returnTypeName}") }
            fo5.fields.take(20).forEach { println("  5Fo field ${it.name}:${it.typeName}") }
        }

        println("=== INSPECT STORY MENU BUILDERS ===")
        val callback = dexkit.getClassData("Lcom/facebook/stories/viewer/ui/buckets/regular/topbar/menu/StoryViewerMoreButtonCallback;")
        if (callback != null) {
            callback.methods.filter { it.returnTypeName == "void" }.forEach { m ->
                println("  callback.${m.name}(${m.paramTypeNames})")
                val adds = m.instructions.filter { it.string != null || it.methodRef?.name == "add" }
                adds.forEach { println("    m=${it.methodRef?.declaredClassName}->${it.methodRef?.name} s=${it.string}") }
            }
        }
        val eVD = dexkit.getClassData("LX/eVD;")
        if (eVD != null) {
            eVD.methods.forEach { m ->
                println("  eVD.${m.name}(${m.paramTypeNames})${m.returnTypeName}")
            }
        }

        println("=== INSPECT StoryCard methods ===")
        val storyCard = dexkit.getClassData("Lcom/facebook/stories/model/StoryCard;")
        if (storyCard != null) {
            println("StoryCard methods total: ${storyCard.methods.size}")
            storyCard.methods.filter { it.returnTypeName.contains("GraphQLMedia") || it.returnTypeName.contains("Media") || it.name.contains("Media", ignoreCase = true) }.forEach {
                println("  ${it.name}(${it.paramTypeNames})${it.returnTypeName}")
            }
            storyCard.fields.filter { it.typeName.contains("GraphQLMedia") || it.typeName.contains("Media") }.forEach {
                println("  field ${it.name}:${it.typeName}")
            }
        }

        println("=== INSPECT X.BBD (StoryCard media) ===")
        val bbd = dexkit.getClassData("LX/BBD;")
        if (bbd != null) {
            println("BBD name: ${bbd.name}")
            println("BBD fields:")
            bbd.fields.forEach { println("  ${it.name}:${it.typeName}") }
            println("BBD methods:")
            bbd.methods.forEach { println("  ${it.name}(${it.paramTypeNames})${it.returnTypeName}") }
        }

        println("=== TEST NEW FINDERS ===")
        val reelSheet = dexkit.findMethod {
            matcher { usingStrings(listOf("fds_control_clear_mode"), StringMatchType.Equals) }
        }.filter { it.returnTypeName == "void" }.distinctBy { it.descriptor }
        println("reelMoreSheetBuilder: ${reelSheet.size} -> ${reelSheet.firstOrNull()?.let { "${it.declaredClassName}->${it.name}(${it.paramTypeNames})" }}")

        val playerParamsInits = dexkit.findMethod {
            matcher {
                declaredClass("com.facebook.video.engine.api.VideoPlayerParams", StringMatchType.Equals, false)
                name = "<init>"
            }
        }.distinctBy { it.descriptor }
        println("videoPlayerParamsConstructors: ${playerParamsInits.size}")

        val storyCapInit = dexkit.findMethod {
            matcher {
                name = "<init>"
                paramTypes("com.facebook.stories.model.StoryBucket", "com.facebook.stories.model.StoryCard")
            }
        }.distinctBy { it.descriptor }
        println("storyCapabilityConstructor: ${storyCapInit.size} -> ${storyCapInit.firstOrNull()?.let { "${it.declaredClassName}->${it.name}(${it.paramTypeNames})" }}")

        val storySave = dexkit.findMethod {
            matcher {
                usingStrings(listOf("PublishingTaskStatusUnknown", "media_type"), StringMatchType.Contains)
            }
        }.filter { method ->
            method.returnTypeName == "void" && method.paramTypeNames.firstOrNull() == "com.facebook.stories.model.StoryCard"
        }.distinctBy { it.descriptor }
        println("storySaveAction: ${storySave.size} -> ${storySave.firstOrNull()?.let { "${it.declaredClassName}->${it.name}(${it.paramTypeNames})" }}")
        val a08 = callback?.methods?.firstOrNull { it.name == "A08" }
        if (a08 != null) {
            println("A08 instructions:")
            a08.instructions.forEach { inst ->
                val m = inst.methodRef
                val f = inst.fieldRef
                val s = inst.string
                if (m != null || f != null || s != null) {
                    println("  m=${m?.declaredClassName}->${m?.name} f=${f?.declaredClassName}->${f?.name}:${f?.typeName} s=$s")
                }
            }
        }

        println("=== INSPECT clw fields ===")
        val clwClass = dexkit.getClassData("LX/clw;")
        if (clwClass != null) {
            println("clw fields:")
            clwClass.fields.forEach { println("  ${it.name}:${it.typeName}") }
            val cRB = dexkit.getClassData("LX/cRB;")
            if (cRB != null) {
                println("cRB methods:")
                cRB.methods.forEach { println("  cRB.${it.name}(${it.paramTypeNames})${it.returnTypeName}") }
            }
        }

        println("=== INSPECT X.CsG and X.eVD->A00 ===")
        val csG = dexkit.getClassData("LX/CsG;")
        if (csG != null) {
            println("CsG fields:")
            csG.fields.forEach { println("  ${it.name}:${it.typeName}") }
            println("CsG methods:")
            csG.methods.forEach { println("  ${it.name}(${it.paramTypeNames})${it.returnTypeName}") }
        }
        val eVDClass = dexkit.getClassData("LX/eVD;")
        val a00 = eVDClass?.methods?.firstOrNull { it.name == "A00" }
        if (a00 != null) {
            println("eVD.A00 instructions:")
            a00.instructions.forEach { inst ->
                val m = inst.methodRef
                val f = inst.fieldRef
                val s = inst.string
                if (m != null || f != null || s != null) {
                    println("  m=${m?.declaredClassName}->${m?.name} f=${f?.declaredClassName}->${f?.name}:${f?.typeName} s=$s")
                }
            }
        }

        println("=== INSPECT X.DUj and A0C ===")
        val dUj = dexkit.getClassData("LX/DUj;")
        if (dUj != null) {
            println("DUj fields:")
            dUj.fields.forEach { println("  ${it.name}:${it.typeName}") }
            println("DUj methods:")
            dUj.methods.forEach { println("  ${it.name}(${it.paramTypeNames})${it.returnTypeName}") }
        }
        val a0c = callback?.methods?.firstOrNull { it.name == "A0C" }
        if (a0c != null) {
            println("A0C instructions:")
            a0c.instructions.forEach { inst ->
                val m = inst.methodRef
                val f = inst.fieldRef
                val s = inst.string
                if (m != null || f != null || s != null) {
                    println("  m=${m?.declaredClassName}->${m?.name} f=${f?.declaredClassName}->${f?.name}:${f?.typeName} s=$s")
                }
            }
        }

        println("=== INSPECT X.1Uf and X.eeL ===")
        val u1f = dexkit.getClassData("LX/1Uf;")
        if (u1f != null) {
            println("1Uf fields: ${u1f.fields.size}")
            u1f.fields.filter { it.name.contains("DOWNLOAD", ignoreCase = true) || it.name == "AHr" || it.name == "AJ1" }.forEach {
                println("  1Uf field: ${it.name}:${it.typeName}")
            }
        }
        val eeL = dexkit.getClassData("LX/eeL;")
        if (eeL != null) {
            eeL.methods.forEach { println("  eeL.${it.name}(${it.paramTypeNames})${it.returnTypeName}") }
        }

        println("=== INSPECT fds_control_download_video (X.bn3) ===")
        val bn3 = dexkit.getClassData("LX/bn3;")
        if (bn3 != null) {
            bn3.methods.forEach { println("  bn3.${it.name}(${it.paramTypeNames})${it.returnTypeName}") }
        }


        println("=== REEL MORE SHEET STRINGS ===")
        for (marker in listOf("fds_control_clear_mode", "fds_control_download_video", "save_story_attempted", "fb_shorts_viewer", "fb_shorts_more_menu")) {
            val found = dexkit.findMethod {
                matcher { usingStrings(listOf(marker), StringMatchType.Contains) }
            }.distinctBy { it.descriptor }
            println("-- $marker: ${found.size}")
            found.take(5).forEach { println("   ${it.declaredClassName}->${it.name}(${it.paramTypeNames})${it.returnTypeName}") }
        }

        println("=== VIDEO PLAYER PARAMS CONSTRUCTORS ===")
        val playerParamsClass = dexkit.getClassData("Lcom/facebook/video/engine/api/VideoPlayerParams;")
        if (playerParamsClass != null) {
            playerParamsClass.methods.filter { it.name == "<init>" }.forEach {
                println("  VideoPlayerParams.<init>(${it.paramTypeNames})")
            }
            playerParamsClass.fields.filter { it.typeName == "java.lang.String" }.forEach {
                println("  VideoPlayerParams String field: ${it.name}:${it.typeName}")
            }
        } else {
            println("  VideoPlayerParams class not found!")
        }

        println("=== VIDEO DATA SOURCE FIELDS ===")
        val dataSourceClass = dexkit.getClassData("Lcom/facebook/video/engine/api/VideoDataSource;")
        if (dataSourceClass != null) {
            dataSourceClass.fields.forEach {
                println("  VideoDataSource field: ${it.name}:${it.typeName}")
            }
        }
    }
}
