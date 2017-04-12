import com.google.inject.AbstractModule
import com.google.inject.name.Names
import repositories.{OrderRepository, OrderRepositoryInMemory}

class Module extends AbstractModule {
  def configure() = {

    bind(classOf[OrderRepository])
      .to(classOf[OrderRepositoryInMemory])
      .asEagerSingleton()
  }
}