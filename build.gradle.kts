modtype = LIB

apis(
  projects.kj.kjlib,
  projects.kj.auto,
  projects.kj.kjlib.kjlibSocket
)

implementations(
  libs.kotlinx.coroutines,
  projects.kj.reflect,
//  projects.k.key
)