package io.github.nexalloy.nexfusion.spotify

import io.github.nexalloy.SkipTest
import io.github.nexalloy.morphe.AccessFlags
import io.github.nexalloy.morphe.Opcode
import io.github.nexalloy.morphe.fingerprint
import io.github.nexalloy.morphe.findClassDirect
import io.github.nexalloy.morphe.findFieldDirect
import io.github.nexalloy.morphe.findMethodDirect
import io.github.nexalloy.morphe.findMethodListDirect
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.enums.UsingType
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

// region Ads

val contextMenuViewModelClass = findClassDirect {
    runCatching {
        fingerprint { strings("ContextMenuViewModel(header=") }
    }.getOrElse {
        fingerprint {
            accessFlags(AccessFlags.CONSTRUCTOR)
            strings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=")
            parameters("L", "Ljava/util/List;", "Z")
        }
    }.declaredClass!!
}

/**
 * The context menu item view model is `ViewModel(available, enabled, isPremiumUpsell, ...)`.
 *
 * Newer app versions no longer return the view model from a `getViewModel` method, so the
 * premium upsell filter skips itself when this field cannot be found.
 */
@get:SkipTest
val premiumUpsellField = findFieldDirect {
    val viewModelClass = findMethod {
        findFirst = true
        matcher { name = "getViewModel" }
    }.single().returnType!!
    viewModelClass.fields.filter { it.typeName == "boolean" }[1]
}

private fun DexKitBridge.sectionsGetters(structureClass: String): List<MethodData> {
    val classes = findClass {
        matcher { className(structureClass, StringMatchType.EndsWith) }
    }
    if (classes.isEmpty()) return emptyList()
    // The list is stored in the `sections_` protobuf field and read by its getter.
    return findMethod {
        searchInClass(classes)
        matcher { addUsingField { name("sections_") } }
    }.filterNot { it.isConstructor }
}

/** Getters of the sections list of the home page model. */
val homeSectionsGetters = findMethodListDirect {
    sectionsGetters("casita.v1.resolved.HomeStructure") + sectionsGetters("homeapi.proto.HomeStructure")
}

/** Getter of the sections list of the browse (search page) model. */
val browseSectionsGetters = findMethodListDirect {
    sectionsGetters("browsita.v1.resolved.BrowseStructure")
}

/**
 * Getter of the sections list of the now playing view scroll feed. The feed embeds a brand-ad
 * card below the lyrics card.
 *
 * Unlike Home/Browse, `NpvScrollStructure` also has an `addSection(Section)` method that reads
 * the backing `sections_` field, so the getter is pinned to the methods without parameters.
 */
val npvSectionsGetter = findMethodDirect {
    findMethod {
        matcher {
            declaredClass("scrollsita.v1.NpvScrollStructure", StringMatchType.EndsWith)
            addUsingField {
                usingType = UsingType.Read
                name = "sections_"
            }
            paramTypes(emptyList())
        }
    }.first { it.returnTypeName != "void" }
}

private fun DexKitBridge.pendragonRequests(requestClass: String) = findMethod {
    matcher {
        name = "apply"
        addInvoke {
            name = "<init>"
            declaredClass(requestClass, StringMatchType.EndsWith)
        }
    }
}

val pendragonRequestMethods = findMethodListDirect {
    pendragonRequests(".FetchMessageRequest") + pendragonRequests(".FetchMessageListRequest")
}

val embeddedAdPlaylistPropertiesConstructor = findMethodDirect {
    findClass {
        matcher { usingStrings("android-embeddedadplaylist", "embedded_ad_placement") }
    }.flatMap { classes ->
        classes.methods.filter { it.isConstructor && it.paramCount > 2 }
    }.single()
}

/**
 * Plugin entries of the now playing view are created with `(String name, Plugin plugin, boolean enabled)`.
 */
val npvPluginEntryConstructors = findMethodListDirect {
    findMethod {
        matcher { usingStrings("EmbeddedAdNpvPlugin") }
    }.flatMap { it.invokes }.filter { method ->
        val parameters = method.paramTypeNames
        method.isConstructor && parameters.size == 3 &&
            parameters[0] == "java.lang.String" && parameters[2] == "boolean"
    }.distinctBy { it.descriptor }
}

// endregion

// region Layout

val navigationTabEnumClass = findClassDirect {
    findClass {
        matcher {
            superClass("java.lang.Enum")
            usingStrings(listOf("HOME", "SEARCH", "YOUR_LIBRARY", "PREMIUM", "CREATE"), StringMatchType.Equals)
        }
    }.single()
}

/**
 * The bottom navigation is created with `(Tab, Tab, Tab, Tab, Tab, int flags)`.
 */
val navigationTabSetConstructor = findMethodDirect {
    val tabEnum = navigationTabEnumClass().name
    val tabClasses = findClass {
        matcher { addField { type(tabEnum) } }
    }.map { it.name }.toSet()

    findMethod {
        matcher {
            name = "<init>"
            paramCount = 6
        }
    }.single { method ->
        val parameters = method.paramTypeNames
        parameters[5] == "int" && parameters.take(5).distinct().singleOrNull() in tabClasses
    }
}

// endregion

// region Misc

/**
 * The method that verifies whether the caller may bind app widgets.
 *
 * Newer app versions no longer check the `BIND_APPWIDGET` permission in the app, so the
 * widget fix skips itself when this cannot be found.
 */
@get:SkipTest
val canBindAppWidgetPermissionMethod = findMethodDirect {
    runCatching {
        fingerprint {
            returns("Z")
            strings("android.permission.BIND_APPWIDGET")
            opcodes(Opcode.AND_INT_LIT8)
        }
    }.getOrElse {
        // Newer versions no longer check the system app flag in the same method.
        fingerprint {
            returns("Z")
            strings("android.permission.BIND_APPWIDGET")
        }
    }
}

// endregion

// region Privacy

val shareCopyUrlMethod = findMethodDirect {
    runCatching {
        fingerprint {
            returns("Ljava/lang/Object;")
            parameters("Ljava/lang/Object;")
            strings("clipboard", "Spotify Link")
            methodMatcher { name = "invokeSuspend" }
        }
    }.getOrElse {
        fingerprint {
            returns("Ljava/lang/Object;")
            parameters("Ljava/lang/Object;")
            strings("clipboard", "createNewSession failed")
            methodMatcher { name = "apply" }
        }
    }
}

val formatAndroidShareSheetUrlMethod = findMethodDirect {
    runCatching {
        // The share intent builder formats the message with `(share data, url) -> String`.
        findMethod {
            matcher {
                accessFlags(0x9) // public static
                returnType = "java.lang.String"
                paramTypes(listOf(null, "java.lang.String"))
                addCaller { usingStrings("android.intent.extra.TEXT", "text/plain") }
            }
        }.filter { method ->
            // `(L, String)` means a reference first parameter, so drop primitive overloads
            // such as resource string getters `(int, String)`.
            method.paramTypeNames.firstOrNull()?.contains('.') == true
        }.single()
    }.recoverCatching {
        fingerprint {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
            returns("Ljava/lang/String;")
            parameters("L", "Ljava/lang/String;")
            literal { '\n'.code }
        }
    }.getOrElse {
        fingerprint {
            accessFlags(AccessFlags.PUBLIC)
            returns("Ljava/lang/String;")
            parameters("Lcom/spotify/share/social/sharedata/ShareData;", "Ljava/lang/String;")
        }
    }
}

// endregion

// region Premium

/**
 * Getter of the account attributes map of the remote config product state. The map values are
 * `AccountAttribute`s whose `value_` fields drive the playback restrictions and the Premium UI.
 *
 * The map itself is wrapped in `Collections.unmodifiableMap`, but the attribute objects are
 * mutable, which is what the patch rewrites.
 */
val productStateAttributesGetter = findMethodDirect {
    fingerprint {
        returns("Ljava/util/Map;")
        classMatcher { descriptor = "Lcom/spotify/remoteconfig/internal/ProductStateProto;" }
    }
}

/**
 * Assembles the query parameters of an artist page. The assembled string contains
 * `checkDeviceCapability=`; asking for `trackRows` adds the popular tracks.
 */
val buildQueryParametersMethod = findMethodDirect {
    findMethod {
        matcher { usingStrings("trackRows", "device_type:tablet") }
    }.single()
}

/**
 * The Google Assistant player-context deserializer.
 *
 * Up to ~9.1.86 this was `…playermodels.ContextJsonAdapter.fromJson(JsonReader):Context`; since
 * ~9.1.90 the class was renamed to `…playermodels.ContextSerializer` and the method to
 * `deserialize`. Both return `com.spotify.player.model.Context`, which the hook post-processes
 * to strip `spotify:station:` URIs. The old opcode matcher stays as a fallback for older builds.
 */
val assistantContextDeserializerMethod = findMethodDirect {
    runCatching {
        fingerprint {
            returns("Lcom/spotify/player/model/Context;")
            methodMatcher {
                name = "deserialize"
                declaredClass(
                    "voiceassistants.playermodels.ContextSerializer", StringMatchType.EndsWith
                )
            }
        }
    }.getOrElse {
        fingerprint {
            opcodes(
                Opcode.INVOKE_STATIC,
                Opcode.MOVE_RESULT_OBJECT,
                Opcode.INVOKE_VIRTUAL,
                Opcode.MOVE_RESULT_OBJECT,
                Opcode.INVOKE_STATIC,
            )
            methodMatcher {
                name = "fromJson"
                declaredClass(
                    "voiceassistants.playermodels.ContextJsonAdapter", StringMatchType.EndsWith
                )
            }
        }
    }
}

/**
 * The generated builder of the player option overrides. Spotify forces shuffle when a voice
 * assistant starts an album or a playlist; the hook clears the flag before the object is built.
 */
val playerOptionOverridesBuilderClass = findClassDirect {
    findClass {
        matcher {
            className(
                "com.spotify.player.model.command.options.AutoValue_PlayerOptionOverrides\$Builder",
                StringMatchType.EndsWith,
            )
        }
    }.single()
}

// endregion

// region Settings

private const val SETTINGS_STATE_SERIAL_NAME = "com.spotify.settings.rxsettings.SettingsState"

/**
 * Locates the boolean field of the settings state class through its generated
 * kotlinx.serialization serializer.
 *
 * The state class is `@Serializable`, so Spotify ships a generated serializer for it: a class
 * that registers the serial name (`com.spotify.settings.rxsettings.SettingsState`) together with
 * every element name (`offline_mode`, `show_unavailable_tracks`, …) in its static initializer and
 * whose `deserialize` method instantiates the (obfuscated) state class from those elements. The
 * element order of the serializer is the constructor parameter order, which in turn is the field
 * declaration order of the state class, so the requested element maps to the field with the same
 * index.
 *
 * This survives obfuscation because serial names are string literals.
 *
 * Ported from Femify (`yobreehs/yfimeF`, `io.github.a092devs.revancedxposed.spotify.misc.Fingerprints`),
 * GPL-3.0.
 */
private fun DexKitBridge.settingsStateFieldBySerializer(elementName: String): FieldData {
    val serializerClass = findMethod { matcher { usingStrings(elementName) } }
        .mapNotNull { it.declaredClass }
        .distinct()
        .firstOrNull { candidate ->
            candidate.methods.any { method ->
                method.invokes.any { it.isConstructor && it.paramCount >= 10 }
            }
        } ?: error("SettingsState serializer not found")

    val descriptorInit = serializerClass.methods.first { it.usingStrings.contains(elementName) }
    val strings = descriptorInit.usingStrings
    val serialNameIndex = strings.indexOf(SETTINGS_STATE_SERIAL_NAME)
    require(serialNameIndex >= 0) { "Serial name not found in ${serializerClass.descriptor}" }
    val elementIndex = strings.indexOf(elementName) - serialNameIndex - 1
    require(elementIndex >= 0) { "Element $elementName not found in ${serializerClass.descriptor}" }

    val stateConstructor = serializerClass.methods
        .flatMap { it.invokes }
        .filter { it.isConstructor && it.paramCount >= 10 }
        .maxByOrNull { it.paramCount }
        ?: error("SettingsState constructor not found in ${serializerClass.descriptor}")
    val stateClass = stateConstructor.declaredClass
        ?: error("SettingsState class not found in ${stateConstructor.descriptor}")

    val fields = stateClass.fields.filter { !Modifier.isStatic(it.modifiers) }
    val field = fields.getOrNull(elementIndex)
        ?: error("No field #$elementIndex in ${stateClass.descriptor}")
    require(field.typeName == "boolean") {
        "Field ${field.descriptor} for $elementName is not boolean"
    }
    return field
}

/**
 * Legacy lookup through the generated `…showUnavailableTracks` property reference: the property
 * reference class is the one that mentions the literal `showUnavailableTracks()Z` and has the
 * `get(Object):Object` accessor that reads the field. Those property references disappeared in
 * Spotify 9.1.92.
 */
private fun DexKitBridge.settingsStateFieldByPropertyReference(propertyName: String): FieldData {
    val propertyReferenceClass = findMethod { matcher { usingStrings("${propertyName}()Z") } }
        .mapNotNull { it.declaredClass }
        .distinct()
        .firstOrNull { candidate ->
            candidate.methods.any { method ->
                method.name == "get" &&
                    method.returnTypeName == "java.lang.Object" &&
                    method.paramTypeNames == listOf("java.lang.Object")
            }
        } ?: error("Property reference class for $propertyName not found")

    val getter = propertyReferenceClass.methods.first { method ->
        method.name == "get" &&
            method.returnTypeName == "java.lang.Object" &&
            method.paramTypeNames == listOf("java.lang.Object")
    }
    return getter.usingFields.single().field
}

/**
 * The boolean field backing Spotify's own "Show unplayable songs" setting (`show_unavailable_tracks`
 * in the server-synced settings state). The state class is obfuscated and its consumers read the
 * field directly, so the patch sets it on every freshly built state.
 */
val showUnavailableTracksField = findFieldDirect {
    runCatching { settingsStateFieldBySerializer("show_unavailable_tracks") }
        .recoverCatching { settingsStateFieldByPropertyReference("showUnavailableTracks") }
        .getOrThrow()
}

// endregion
