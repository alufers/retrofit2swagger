val commonTypeSignatures: List<CommonTypeWrappersLocator.Signature> = listOf(
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(CommonTypeWrappersLocator.CommonTypeKind.TRANSPARENT_WRAPPER),
        friendlyName = "Observable",
        sourceLibrary = "rxjava",
        probableRawStrings = mapOf("new BlockingFirstObserver" to 10.0),
        probableStringLiterals = mapOf(
            "observer is null" to 10.0,
            "The RxJavaPlugins.onSubscribe hook returned a null Observer. Please change the handler provided to RxJavaPlugins.setOnObservableSubscribe for invalid null returns. Further reading: https://github.com/ReactiveX/RxJava/wiki/Plugins" to 20.0
        ),
    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(CommonTypeWrappersLocator.CommonTypeKind.TRANSPARENT_WRAPPER),
        friendlyName = "WeirdAndOldObservable",
        sourceLibrary = "rxjava",
        probableRawStrings = mapOf("new ActionSubscriber(" to 5.0),
        probableStringLiterals = mapOf(
            "] and then again while trying to pass to onError." to 15.0,
            "start + count can not exceed Integer.MAX_VALUE" to 10.0,
            "capacityHint > 0 required but it was " to 10.0,
            "Error occurred attempting to subscribe [" to 5.0,
            "onNext can not be null" to 2.0,
            "onSubscribe function can not be null." to 2.0,
        ),
    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(CommonTypeWrappersLocator.CommonTypeKind.TRANSPARENT_WRAPPER),
        friendlyName = "Completable",
        sourceLibrary = "rxjava",
        probableStringLiterals = mapOf(
            "observer is null" to 10.0,
            "completionValueSupplier is null" to 5.0,
            "The RxJavaPlugins.onSubscribe hook returned a null CompletableObserver. Please check the handler provided to RxJavaPlugins.setOnCompletableSubscribe for invalid null returns. Further reading: https://github.com/ReactiveX/RxJava/wiki/Plugins" to 20.0
        ),
    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(CommonTypeWrappersLocator.CommonTypeKind.TRANSPARENT_WRAPPER),
        friendlyName = "Single",
        sourceLibrary = "rxjava",
        probableRawStrings = mapOf("new NullPointerException(\"subscribeActual failed\")" to 10.0),
        probableStringLiterals = mapOf(
            "source is null" to 2.0,
            "The RxJavaPlugins.onSubscribe hook returned a null SingleObserver. Please check the handler provided to RxJavaPlugins.setOnSingleSubscribe for invalid null returns. Further reading: https://github.com/ReactiveX/RxJava/wiki/Plugins" to 20.0,
            "The RxJavaPlugins.onSubscribe hook returned a null MaybeObserver. Please check the handler provided to RxJavaPlugins.setOnMaybeSubscribe for invalid null returns. Further reading: https://github.com/ReactiveX/RxJava/wiki/Plugins" to -20.0,
        ),
    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(
            CommonTypeWrappersLocator.CommonTypeKind.TRANSPARENT_WRAPPER,
            CommonTypeWrappersLocator.CommonTypeKind.FORCE_SWAGGER_TYPE
        ),
        friendlyName = "Call",
        sourceLibrary = "retrofit2",
        rawStringsHeuristicOnly = mapOf(
            "throws IOException" to 2.0,
            "public interface " to 2.0,
            "Request request()" to 8.0,

            ),
        probableRawStrings = mapOf(
            "interface Call<T> extends Cloneable" to 2.0,
            "import okhttp3.Request" to 4.0,
            "extends Cloneable" to 4.0,
        ),
        isInterfaceHeuristicScore = 5.0,
        swaggerType = SwaggerSchema.SwaggerType.FreeFormObject,
        hasTypeParametersHeuristicScore = 7.0
    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(CommonTypeWrappersLocator.CommonTypeKind.TRANSPARENT_WRAPPER),
        friendlyName = "Response",
        sourceLibrary = "retrofit2",
        probableStringLiterals = mapOf(
            "rawResponse should not be successful response" to 10.0,
            "rawResponse must be successful response" to 10.0,
            "Response.success()" to 4.0,
            "rawResponse == null" to 2.0,
        ),
    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(CommonTypeWrappersLocator.CommonTypeKind.FORCE_SWAGGER_TYPE),
        friendlyName = "ResponseBody",
        sourceLibrary = "okhttp3",
        probableStringLiterals = mapOf(
            "Cannot buffer entire body for content length: " to 12.0,
            "Stream closed" to 2.0,
            ") and stream length (" to 4.0,
            "rawResponse == null" to 2.0,
            "Stream closed" to 5.0,
        ),
        probableStringLiteralsHeuristicOnly = mapOf(
            "locally-initiated streams shouldn't have headers yet" to -15.0,
            "] windowUpdate" to -10.0
        ),
        rawStringsHeuristicOnly = mapOf(
            "throw new NullPointerException(\"source == null\")" to 1.0
        ),
        swaggerType = SwaggerSchema.SwaggerType.FreeFormObject

    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(CommonTypeWrappersLocator.CommonTypeKind.FORCE_SWAGGER_TYPE),
        friendlyName = "LocalTime",
        sourceLibrary = "org.threeten.bp",
        probableStringLiterals = mapOf(
            "Unable to obtain LocalTime from TemporalAccessor: " to 16.0,

            ),
        probableStringLiteralsHeuristicOnly = mapOf(
            ", type " to 3.0,
            "Deserialization via serialization delegate" to 2.0,
            "Unable to obtain LocalDate from TemporalAccessor: " to -15.0
        ),
        swaggerType = SwaggerSchema.SwaggerType(
            type = "string",
            format = "time"
        )
    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(CommonTypeWrappersLocator.CommonTypeKind.FORCE_SWAGGER_TYPE),
        friendlyName = "LocalDate",
        sourceLibrary = "org.threeten.bp",
        minHeuristicScore = 30.0,
        probableStringLiterals = mapOf(
            "Invalid date 'February 29' as '" to 16.0,
            "Unable to obtain LocalDate from TemporalAccessor: " to 15.0
        ),
        probableStringLiteralsHeuristicOnly = mapOf(
            "Invalid date 'DayOfYear 366' as '" to 5.0,
            "Deserialization via serialization delegate" to 2.0,
        ),
        swaggerType = SwaggerSchema.SwaggerType(
            type = "string",
            format = "date"
        )
    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(CommonTypeWrappersLocator.CommonTypeKind.FORCE_SWAGGER_TYPE),
        friendlyName = "LocalDateTime",
        sourceLibrary = "org.threeten.bp",
        probableStringLiterals = mapOf(
            "Unable to obtain LocalDateTime from TemporalAccessor: " to 20.0
        ),
        probableStringLiteralsHeuristicOnly = mapOf(
            "Invalid date 'DayOfYear 366' as '" to 5.0,
            "Deserialization via serialization delegate" to 2.0,
            "offset" to 1.0,
            "time" to 1.0,
            "date" to 1.0,
        ),
        swaggerType = SwaggerSchema.SwaggerType(
            type = "string",
            format = "date-time"
        )
    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(CommonTypeWrappersLocator.CommonTypeKind.FORCE_SWAGGER_TYPE),
        friendlyName = "ZonedDateTime",
        sourceLibrary = "org.threeten.bp",
        probableStringLiterals = mapOf(
            "ZoneId must match ZoneOffset" to 20.0
        ),
        probableStringLiteralsHeuristicOnly = mapOf(
            "localDateTime" to 5.0,
            "ZoneOffset '" to 7.0,
            "offset" to 1.0,
            "zone" to 1.0,
            "localDateTime" to 1.0,
        ),
        swaggerType = SwaggerSchema.SwaggerType(
            type = "string",
            format = "date-time"
        )
    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(
            CommonTypeWrappersLocator.CommonTypeKind.FORCE_SWAGGER_TYPE,
            CommonTypeWrappersLocator.CommonTypeKind.IGNORE_WHEN_EXTENDED
        ),
        friendlyName = "BaseObservable",
        sourceLibrary = "androidx.databinding",
        probableRawStrings = mapOf(
            "addOnPropertyChangedCallback(Observable.OnPropertyChangedCallback onPropertyChangedCallback)" to 15.0
        ),
        swaggerType = SwaggerSchema.SwaggerType.FreeFormObject
    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(),
        friendlyName = "TypeAdapters",
        sourceLibrary = "com.google.gson.internal",
        probableStringLiterals = mapOf(
            "Attempted to deserialize a java.lang.Class. Forgot to register a type adapter?" to 10.0,
            "Attempted to serialize java.lang.Class: " to 10.0,
            ". Forgot to register a type adapter?" to 10.0,
        ),
        probableStringLiteralsHeuristicOnly = mapOf(
            "Expecting character, got: " to 5.0,
            "Expecting number, got: " to 5.0,
            "Couldn't write " to 5.0,
        )
    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(
            CommonTypeWrappersLocator.CommonTypeKind.ALIAS_ANNOTATION
        ),
        friendlyName = "SerializedName",
        sourceLibrary = "com.google.gson.annotations",
        performHeuristicSearch = false
    ),
    CommonTypeWrappersLocator.Signature(
        kinds = listOf(
            CommonTypeWrappersLocator.CommonTypeKind.ALIAS_ANNOTATION
        ),
        friendlyName = "JsonProperty",
        sourceLibrary = "com.fasterxml.jackson",
        isAnnotationHeuristicScore = 1.0,
        probableRawStrings = mapOf(
            "public @interface JsonProperty {" to 15.0,
            "@JacksonAnnotation" to 5.0
        ),
        rawStringsHeuristicOnly =  mapOf(
            "String value() default \"\"" to 5.0,
            "public enum Access" to 5.0,
        )
    )
)