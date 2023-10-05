@file:Suppress("UNUSED_VARIABLE", "LocalVariableName")
@file:OptIn(ExperimentalSerializationApi::class)

package documentation.manual.gettingstarted

import com.jillesvangurp.ktsearch.*
import documentation.sourceGitRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

val whatIsKtSearchMd = sourceGitRepository.md {
    includeMdFile("whatisktsearch.md")
}

@OptIn(ExperimentalSerializationApi::class)
val gettingStartedMd = sourceGitRepository.md {
    +"""
        To get started, simply add the dependency to your project and create a client. 
        The process is the same for both jvm and kotlin-js.
    """.trimIndent()
    includeMdFile("../../projectreadme/gradle.md")
    section("Create a Client") {
        +"""
            To use `kt-search` you need a `SearchClient` instance. Similar to what the Elastic and Opensearch Java client do, there is a
            simple `RestClient` interface that currently has a default implementation based on `ktor-client`. This client
            takes care of sending HTTP calls to your search cluster.
        """.trimIndent()
        block {
            // creates a client with the default RestClient
            val client = SearchClient()
        }

        // test server runs on 9999, so we need to override
        val client = SearchClient(KtorRestClient(Node("localhost", 9999)))

        +"""
            After creating the client, you can use it. Since kt-search uses non blocking IO via ktor client, all 
            calls are suspending and have to be inside a co-routine.
        """.trimIndent()

        +"""
            You may want to override some of the default parameter values. For example, this is how you would
            connect to your cluster in Elastic Cloud using either a user and password and basic authentication or an api key.
        """.trimIndent()
        block {
            val client2 = SearchClient(
                KtorRestClient(
                    https = true,
                    user = "alice",
                    password = "secret",
                    nodes = arrayOf(Node("xxxxx.europe-west3.gcp.cloud.es.io", 9243))
                )
            )
        }

        +"""
            Note, for Opensearch clusters in AWS, you need to use Amazon's [sigv4](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_aws-signing.html) to sign requests. This is currently 
            not supported directly in the client. Also see this [gist](https://gist.github.com/hassaku63/e3ed3cac288d429563cdddf1768613d6) on how
             to do curl requests.
            
            To work around this, you can provide your own customized ktor client that 
             does this; or provide an alternative `RestClient` implementation in case you don't want to use ktor client. 
             Pull requests that document authenticating with opensearch in AWS in more detail 
             or enable this in a multi platform way with the ktor client are welcome. I currently
             do not have access to an opensearch cluster in AWS.
            
            
            `KtorRestClient` has an alternate constructor that you can use if
             you have a proxy in front of your cluster or only one node.
        """.trimIndent()
        block {
            val client3 = SearchClient(KtorRestClient("127.0.0.1", 9200))
        }

        +"""
            You can also use multiple nodes and use a node selection strategy.
        """.trimIndent()

        block {
            val nodes= arrayOf(
                Node("127.0.0.1", 9200),
                Node("127.0.0.1", 9201),
                Node("127.0.0.1", 9202)
            )
            val client4 = SearchClient(
                KtorRestClient(
                    nodes = nodes,
                    nodeSelector = RoundRobinNodeSelector(nodes),
                )
            )
        }
        +"""
            There are currently just one NodeSelector implementation that implements a simple round robin
            strategy. Note, it currently does not attempt to detect failing nodes or implements any cluster 
            sniffing. This is something that may be added later (pull requests welcome). 
            
            You can easily add your own node selection strategy by implementing the `NodeSelector` interface.
        """.trimIndent()

        block {
            runBlocking {
                client.root().let { resp ->
                    println("${resp.variantInfo.variant}: ${resp.version.number}")
                }
                client.clusterHealth().let { resp ->
                    println(resp.clusterName + " is " + resp.status)
                }
            }
        }
    }

    section("SniffingNodeSelector") {
        +"""
            The client includes a SniffingNodeSelector that you may use with multi 
            node clusters without a load balancer.
        """.trimIndent()

        suspendingBlock(runBlock = false) {
            val nodes= arrayOf(
                Node("localhost", 9200),
                Node("127.0.0.1", 9201)
            )
            val client5 = SearchClient(
                KtorRestClient(
                    nodes = nodes,

                    nodeSelector = SniffingNodeSelector(
                        initialNodes = nodes,
                        maxNodeAge = 3.seconds
                    ),
                )
            )
            coroutineScope {
                async(AffinityId("myid")) {
                    // always ends up using the same node
                    client5.root()
                }
                // without AffinityId:
                //  - on jvm: uses the thread name as the affinityId
                //  - on js: randomly picks a node
                // with both it periodically refreshes its list of nodes
                async {
                    client5.root()
                }
            }
        }

        +"""
            The SniffingNodeSelector tries to give threads and co routine scopes with the AffinityId scope
            the same node. This may help performance a bit if you do multiple elastic search calls
            in one web request or transaction.
        """.trimIndent()
    }

    section("Customizing the ktor rest client") {
        +"""
            One of the parameters on `KtorRestClient` is the client parameter which has a default value
            that depends on a default with actual implementations for Javascript and Jvm targets. Of course,
            you can override this to further customize this. 
            
            On the JVM, you can choose from several engines. We recently switched from the CIO engine to the 
            Java engine that was added in Java 11. This is a non blocking IO http client.
             
            The old CIO client is also still included.
        """.trimIndent()
    }

    section("JSON handling") {
        +"""
            The `SearchClient` has a `json` parameter with a kotlinx.serialization `Json`.
            The default value for this is a carefully constructed instance that is configured
            to be lenient and do the right thing with e.g. nulls and default values. But you 
            can of course use your own instance should you need to. Note, the kotlinx.serialization defaults are pretty 
            terrible for the real world. So, beware if you provide a custom instance.
                       
            There are two instances included with this library that are used by default that you may use here:
            
            - `DEFAULT_JSON` this is what is used by default
            - `DEFAULT_PRETTY_JSON` a pretty printing variant of DEFAULT_JSON that otherwise has the same settings.
        """.trimIndent()

        block {
            val DEFAULT_JSON = Json {
                // don't rely on external systems being written in kotlin
                // or even having a language with default values
                // the default of false is insane and dangerous
                encodeDefaults = true
                // save space
                prettyPrint = false
                // people adding things to the json is OK, we're forward compatible
                // and will just ignore it
                isLenient = true
                // encoding nulls is meaningless and a waste of space.
                explicitNulls = false
                // adding new enum values is OK even if older clients won't understand it
                // they should be forward compatible
                ignoreUnknownKeys = true
                // decode missing enum values as null
                coerceInputValues = true
            }
        }
    }
}