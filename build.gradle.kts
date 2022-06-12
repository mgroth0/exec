modtype = LIB

dependencies {
  api(projects.kj.kjlib)
  implementation(projects.kj.json)
  api(projects.kj.auto)
  api(projects.kj.kjlib.kjlibSocket)
  implementation(libs.kotlinx.coroutines)
  implementation(projects.kj.reflect)
  implementation(projects.kj.async)
}