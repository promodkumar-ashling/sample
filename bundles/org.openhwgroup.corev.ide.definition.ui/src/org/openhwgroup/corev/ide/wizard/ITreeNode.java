package org.openhwgroup.corev.ide.wizard;

import java.util.List;
/**
 * @author Vijay
 *
 */
public interface ITreeNode
{
	public String getName();

	public List getChildren();

	public boolean hasChildren();

	public ITreeNode getParent();
}