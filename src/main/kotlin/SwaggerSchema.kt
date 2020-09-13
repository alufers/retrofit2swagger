import com.github.javaparser.ast.type.Type
import kotlinx.serialization.*
import kotlinx.serialization.json.*


interface SwaggerWithChildren {
    fun children(): List<SwaggerWithChildren>;
}

@Serializable
class SwaggerSchema(val openapi: String) : SwaggerWithChildren {


    var info: SchemaInfo = SchemaInfo("Extracted from retrofit", "ddd", "1.0.0")

    @Serializable
    class SchemaInfo(val title: String, val description: String, val version: String) : SwaggerWithChildren {
        override fun children(): List<SwaggerWithChildren> {
            return listOf()
        }

    }

    @Serializable
    data class ServerSpec(public val url: String) : SwaggerWithChildren {
        override fun children(): List<SwaggerWithChildren> {
            return listOf()
        }
    }

    val servers = mutableListOf<ServerSpec>()
    val paths: MutableMap<String, MutableMap<String, PathOperation>> = mutableMapOf()

    @Serializable
    class Components() : SwaggerWithChildren {
        val schemas: MutableMap<String, SwaggerType> = mutableMapOf()
        override fun children(): List<SwaggerWithChildren> {
            return schemas.values.toList()
        }

    }

    val components = Components()

    override fun children(): List<SwaggerWithChildren> {
        return listOf(info, components) + servers + paths.values.map { it.values }.flatten()
    }

    @Serializable
    class PathOperation(
        var summary: String = "",
        var description: String = "",
        var parameters: MutableList<Parameter> = mutableListOf(),
        var responses: MutableMap<String, ResponseBody> = mutableMapOf(),
        var requestBody: RequestBody? = null,
        var deprecated: Boolean? = false,
        var servers: MutableList<ServerSpec>? = null,
        @SerialName("x-retrofit-interface")
        var xRetrofitInterface: String? = null,
        @Transient
        var isMultipart: Boolean = false
    ) : SwaggerWithChildren {
        override fun children(): List<SwaggerWithChildren> {
            val c = mutableListOf<SwaggerWithChildren>()
            if (requestBody != null) {
                c.add(requestBody!!)
            }
            return c + responses.values + parameters
        }

        @Serializable
        class Parameter(
            var name: String,
            @SerialName("in") var paramIn: ParameterPlace,
            var description: String,
            var required: Boolean = false,
            var deprecated: Boolean = false,
            var schema: SwaggerType? = null
        ) : SwaggerWithChildren {
            @Serializable
            enum class ParameterPlace {
                @SerialName("query")
                Query,

                @SerialName("header")
                Header,

                @SerialName("path")
                Path,

                @SerialName("cookie")
                Cookie
            }

            override fun children(): List<SwaggerWithChildren> {
                return listOfNotNull(schema)
            }
        }


        @Serializable
        class RequestBody(val content: MediaType, val description: String = "", val required: Boolean = false) :
            SwaggerWithChildren {
            override fun children(): List<SwaggerWithChildren> {
                return listOf(content)
            }
        }

        @Serializable
        class ResponseBody(
            val content: MediaType,
            val description: String = "",
            val headers: Map<String, HeaderObject> = mapOf()

        ) : SwaggerWithChildren {
            override fun children(): List<SwaggerWithChildren> {
                return headers.values + listOf<SwaggerWithChildren>()
            }
        }

        @Serializable
        class HeaderObject(val description: String?, val schema: SwaggerType?) : SwaggerWithChildren {
            override fun children(): List<SwaggerWithChildren> {
                return listOfNotNull(schema)
            }
        }

        @Serializable
        class MediaType(
            @SerialName("application/json") val json: MediaTypeInner? = null,
            @SerialName("multipart/form-data") val multipart: MediaTypeInner? = null
        ) : SwaggerWithChildren {
            @Serializable
            class MediaTypeInner(val schema: SwaggerType?) : SwaggerWithChildren {
                override fun children(): List<SwaggerWithChildren> {
                    return listOfNotNull(schema)
                }
            }

            override fun children(): List<SwaggerWithChildren> {
                return listOfNotNull(json)
            }
        }
    }

    @Serializable
    open class SwaggerType(
        val type: String = "",
        val enum: List<String> = listOf(),
        val properties: MutableMap<String, SwaggerType> = mutableMapOf(),
        val items: SwaggerType? = null,
        @SerialName("\$ref") var ref: String? = null,
        val example: String? = null,
        val additionalProperties: SwaggerType? = null,
        val format: String? = null,
        val allOf: MutableList<SwaggerType> = mutableListOf()
    ) : SwaggerWithChildren {

        class TypeReferenceToResolve(@Transient val theType: Type) : SwaggerType()

        override fun children(): List<SwaggerWithChildren> {
            var c = listOf<SwaggerWithChildren>()
            if (items != null) {
                c = c + items
            }
            c = c + properties.values
            return c
        };

        companion object {
            val FreeFormObject = SwaggerSchema.SwaggerType(
                type = "object",
                additionalProperties = SwaggerSchema.SwaggerType()
            )
        }
    }


}