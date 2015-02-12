package daos;

public interface IAbstractDao<T> {

	public abstract void refresh(T entity);

}