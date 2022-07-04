modtype = LIB

apis(
  projects.k.kjlib,
  projects.k.auto,
  projects.k.kjlib.kjlibSocket
)

implementations(
  libs.kotlinx.coroutines,
  projects.k.reflect,
//  projects.k.key

)